package ir.phonehunter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.config.HunterProperties;
import ir.phonehunter.divar.DivarClient;
import ir.phonehunter.divar.DivarResponseParser;
import ir.phonehunter.extract.PhoneSpecExtractor;
import ir.phonehunter.extract.RiskRules;
import ir.phonehunter.llm.LlmAdvisor;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.Deal;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;
import ir.phonehunter.notify.MessageFormatter;
import ir.phonehunter.notify.TelegramNotifier;
import ir.phonehunter.store.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Agent Loop:  Tool call (fetch Divar) → Execute (parse + store) → Observe (median per group)
 * → Decide (deal rules + risk rules + LLM review) → Act (Telegram) → repeat on a schedule.
 */
@Component
public class HunterAgent {

    private static final Logger log = LoggerFactory.getLogger(HunterAgent.class);

    private record Candidate(RawListing raw, PhoneSpec spec, Condition condition, long price,
                             ListingRepository.Change change) {
    }

    private final HunterProperties props;
    private final DivarClient divar;
    private final PhoneSpecExtractor extractor;
    private final RiskRules risk;
    private final StatsCalculator statsCalc;
    private final DealEvaluator evaluator;
    private final LlmAdvisor llm;
    private final TelegramNotifier telegram;
    private final ListingRepository repo;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger iteration = new AtomicInteger();

    public HunterAgent(HunterProperties props, DivarClient divar, PhoneSpecExtractor extractor, RiskRules risk,
                       StatsCalculator statsCalc, DealEvaluator evaluator, LlmAdvisor llm,
                       TelegramNotifier telegram, ListingRepository repo) {
        this.props = props;
        this.divar = divar;
        this.extractor = extractor;
        this.risk = risk;
        this.statsCalc = statsCalc;
        this.evaluator = evaluator;
        this.llm = llm;
        this.telegram = telegram;
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        log.info("Hunter ready — city={} ({}), queries={}, LLM={}", props.getCity().getName(),
                props.getCity().getId(), props.getQueries().size(), llm.name());
        telegram.send("🤖 شکارچی دیوار روشن شد\nشهر: " + props.getCity().getName()
                + "\nمدل‌ها: " + String.join("، ", props.getQueries())
                + "\nبررسی هوش مصنوعی: " + llm.name());
    }

    @Scheduled(initialDelayString = "${hunter.poll.initial-delay-ms:5000}",
            fixedDelayString = "${hunter.poll.interval-ms:300000}")
    public void scheduledRun() {
        runOnce();
    }

    /** One full loop iteration. Safe to call manually (e.g. from the REST endpoint). */
    public Map<String, Object> runOnce() {
        if (!running.compareAndSet(false, true)) {
            return Map.of("status", "already running");
        }
        try {
            return iterate();
        } finally {
            running.set(false);
        }
    }

    private Map<String, Object> iterate() {
        int n = iteration.incrementAndGet();
        String cityId = props.getCity().getId();
        boolean bootstrap = repo.isEmpty(cityId);
        int pages = bootstrap ? props.getPoll().getBootstrapPages() : props.getPoll().getPagesPerQuery();
        long now = System.currentTimeMillis();
        log.info("── Iteration #{} {}— {} pages/query", n, bootstrap ? "(bootstrap, collecting history) " : "", pages);

        int fetched = 0;
        int parsed = 0;
        List<Candidate> fresh = new ArrayList<>();

        // 1) TOOL CALL + EXECUTE: fetch newest ads for every query and store them
        for (String query : props.getQueries()) {
            JsonNode pagination = null;
            for (int page = 1; page <= pages; page++) {
                DivarResponseParser.Page result;
                try {
                    result = divar.search(cityId, props.getCategory(), query, pagination);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Map.of("status", "interrupted");
                } catch (Exception e) {
                    log.warn("Search '{}' page {} failed: {}", query, page, e.getMessage());
                    break;
                }
                fetched += result.listings().size();
                for (RawListing raw : result.listings()) {
                    Optional<Candidate> c = interpret(raw, cityId, now);
                    if (c.isPresent()) {
                        parsed++;
                        if (c.get().change() != ListingRepository.Change.SEEN) {
                            fresh.add(c.get());
                        }
                    }
                }
                if (!result.hasNext()) {
                    break;
                }
                pagination = result.nextPagination();
            }
        }

        // 2) OBSERVE + DECIDE
        long since = now - Duration.ofDays(props.getStats().getWindowDays()).toMillis();
        Map<String, Optional<PriceStats>> statsCache = new HashMap<>();
        Map<DealEvaluator.Outcome, Integer> outcomes = new EnumMap<>(DealEvaluator.Outcome.class);
        int notified = 0;
        boolean mayNotify = !bootstrap || props.getDeal().isNotifyOnBootstrap();

        for (Candidate c : fresh) {
            String key = c.spec().model() + "|" + c.spec().storageGb() + "|" + c.condition();
            Optional<PriceStats> stats = statsCache.computeIfAbsent(key,
                    k -> statsCalc.compute(repo.prices(cityId, c.spec(), c.condition(), since)));
            DealEvaluator.Result r = evaluator.evaluate(c.raw(), c.spec(), c.condition(), c.price(), stats.orElse(null));
            outcomes.merge(r.outcome(), 1, Integer::sum);
            if (r.deal().isEmpty() || !mayNotify || !repo.shouldNotify(c.raw().token(), c.price())) {
                continue;
            }
            Optional<Deal> reviewed = review(r.deal().get());
            if (reviewed.isEmpty()) {
                continue;
            }
            Deal deal = reviewed.get();
            // 3) ACT
            if (telegram.send(MessageFormatter.format(deal, props.getCity().getName()))) {
                repo.markNotified(c.raw().token(), c.price(), deal.aiNote(), System.currentTimeMillis());
                notified++;
            }
        }

        log.info("── Iteration #{} done: fetched={}, in-scope={}, new/price-drop={}, outcomes={}, notified={}",
                n, fetched, parsed, fresh.size(), outcomes, notified);
        return Map.of("iteration", n, "bootstrap", bootstrap, "fetched", fetched, "inScope", parsed,
                "fresh", fresh.size(), "outcomes", outcomes, "notified", notified);
    }

    private Optional<Candidate> interpret(RawListing raw, String cityId, long now) {
        Optional<PhoneSpec> spec = extractor.extract(raw.title(), null);
        if (spec.isEmpty()) {
            return Optional.empty();
        }
        Condition condition = Condition.fromDivarText(raw.conditionText());
        if (condition == Condition.UNKNOWN || condition == Condition.NEEDS_REPAIR) {
            return Optional.empty();
        }
        Long price = ir.phonehunter.util.PriceParser.parseToman(raw.priceText());
        if (price == null) {
            return Optional.empty();
        }
        boolean risky = risk.check(raw.title()).rejected();
        ListingRepository.Change change = repo.upsert(raw.token(), cityId, spec.get(), condition, price,
                raw.title(), risky, now);
        return risky ? Optional.empty() : Optional.of(new Candidate(raw, spec.get(), condition, price, change));
    }

    /** Second look at a candidate: full description → rules → LLM. Empty = rejected. */
    private Optional<Deal> review(Deal deal) {
        String token = deal.listing().token();
        String description = divar.fetchDescription(token);
        RiskRules.Verdict v = risk.check(deal.listing().title() + "\n" + description);
        if (v.rejected()) {
            log.info("Rejected {} ({}) by rules: {}", token, deal.spec().label(), v.hard());
            repo.markRisky(token);
            return Optional.empty();
        }
        Optional<LlmAdvisor.Review> ai = llm.review(deal, description);
        if (ai.isPresent() && ai.get().skip()) {
            log.info("Rejected {} ({}) by LLM: {}", token, deal.spec().label(), ai.get().reason());
            repo.markRisky(token);
            return Optional.empty();
        }
        return Optional.of(deal.withReview(v.soft(), ai.map(LlmAdvisor.Review::reason).orElse(null)));
    }
}
