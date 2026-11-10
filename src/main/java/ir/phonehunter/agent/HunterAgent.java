package ir.phonehunter.agent;

import com.fasterxml.jackson.databind.JsonNode;
import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.analysis.StatsSelector;
import ir.phonehunter.config.HunterProperties;
import ir.phonehunter.divar.DivarClient;
import ir.phonehunter.divar.DivarResponseParser;
import ir.phonehunter.extract.PhoneSpecExtractor;
import ir.phonehunter.extract.RegistryRegionDetector;
import ir.phonehunter.extract.RiskRules;
import ir.phonehunter.llm.LlmAdvisor;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.Deal;
import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;
import ir.phonehunter.model.Region;
import ir.phonehunter.model.Registry;
import ir.phonehunter.notify.MessageFormatter;
import ir.phonehunter.notify.TelegramNotifier;
import ir.phonehunter.store.ListingRepository;
import ir.phonehunter.util.PriceParser;
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
 * The Agent Loop:  Tool call (fetch Divar) → Execute (parse + classify + store) → Observe (median of
 * the right market group) → Decide (deal rules + risk rules + LLM review) → Act (Telegram) → repeat.
 */
@Component
public class HunterAgent {

    private static final Logger log = LoggerFactory.getLogger(HunterAgent.class);

    private record Candidate(RawListing raw, MarketGroup group, long price, ListingRepository.Change change) {
    }

    /** Per-iteration cache of group statistics. */
    private final class StatsCache {
        private final String cityId;
        private final long since;
        private final Map<String, Optional<PriceStats>> map = new HashMap<>();

        StatsCache(String cityId, long since) {
            this.cityId = cityId;
            this.since = since;
        }

        Optional<StatsSelector.Selected> select(MarketGroup g) {
            Optional<PriceStats> fine = map.computeIfAbsent("F|" + g.fineKey(),
                    k -> statsCalc.compute(repo.prices(cityId, g, since)));
            return StatsSelector.choose(g, fine,
                    () -> map.computeIfAbsent("C|" + g.coarseKey(),
                            k -> statsCalc.compute(repo.coarsePrices(cityId, g, since))),
                    props.getDeal().getMinSamples());
        }
    }

    private final HunterProperties props;
    private final DivarClient divar;
    private final PhoneSpecExtractor extractor;
    private final RegistryRegionDetector registryRegion;
    private final RiskRules risk;
    private final StatsCalculator statsCalc;
    private final DealEvaluator evaluator;
    private final LlmAdvisor llm;
    private final TelegramNotifier telegram;
    private final ListingRepository repo;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger iteration = new AtomicInteger();

    public HunterAgent(HunterProperties props, DivarClient divar, PhoneSpecExtractor extractor,
                       RegistryRegionDetector registryRegion, RiskRules risk, StatsCalculator statsCalc,
                       DealEvaluator evaluator, LlmAdvisor llm, TelegramNotifier telegram, ListingRepository repo) {
        this.props = props;
        this.divar = divar;
        this.extractor = extractor;
        this.registryRegion = registryRegion;
        this.risk = risk;
        this.statsCalc = statsCalc;
        this.evaluator = evaluator;
        this.llm = llm;
        this.telegram = telegram;
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        HunterProperties.DealRules d = props.getDeal();
        String mode = d.isAdaptive()
                ? "تطبیقی (%.1f × پراکندگی، بین %.0f٪ تا %.0f٪)".formatted(d.getAdaptiveFactor(),
                d.getAdaptiveFloorPercent(), d.getAdaptiveCeilingPercent())
                : "ثابت " + d.getMinDiscountPercent() + "٪";
        log.info("Hunter ready — city={} ({}), queries={}, threshold={}, LLM={}", props.getCity().getName(),
                props.getCity().getId(), props.getQueries().size(), mode, llm.name());
        telegram.send("🤖 شکارچی دیوار روشن شد\nشهر: " + props.getCity().getName()
                + "\nمدل‌ها: " + String.join("، ", props.getQueries())
                + "\nآستانه: " + mode
                + "\nرجیستر نشده‌ها: " + (d.isHuntUnregistered() ? "بررسی می‌شوند" : "فقط در آمار")
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

        // 1) TOOL CALL + EXECUTE: fetch newest ads for every query, classify and store them
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
        StatsCache cache = new StatsCache(cityId, since);
        Map<DealEvaluator.Outcome, Integer> outcomes = new EnumMap<>(DealEvaluator.Outcome.class);
        int notified = 0;
        boolean mayNotify = !bootstrap || props.getDeal().isNotifyOnBootstrap();

        for (Candidate c : fresh) {
            DealEvaluator.Result r = decide(c, cache);
            outcomes.merge(r.outcome(), 1, Integer::sum);
            if (r.deal().isEmpty() || !mayNotify || !huntable(c.group())
                    || !repo.shouldNotify(c.raw().token(), c.price())) {
                continue;
            }
            Optional<Deal> reviewed = review(c, r.deal().get(), cache);
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

    private boolean huntable(MarketGroup g) {
        return g.registry() != Registry.NOT_REGISTERED || props.getDeal().isHuntUnregistered();
    }

    private DealEvaluator.Result decide(Candidate c, StatsCache cache) {
        Optional<StatsSelector.Selected> sel = cache.select(c.group());
        DealEvaluator.Result r = evaluator.evaluate(c.raw(), c.group(), c.price(),
                sel.map(StatsSelector.Selected::stats).orElse(null));
        if (r.deal().isPresent()) {
            return new DealEvaluator.Result(r.outcome(), Optional.of(r.deal().get().withStatsSource(sel.get().coarse())));
        }
        return r;
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
        Long price = PriceParser.parseToman(raw.priceText());
        if (price == null) {
            return Optional.empty();
        }
        MarketGroup group = new MarketGroup(spec.get(), condition,
                registryRegion.registry(raw.title()), registryRegion.region(spec.get().model(), raw.title()));
        boolean risky = risk.check(raw.title()).rejected();
        ListingRepository.Change change = repo.upsert(raw.token(), cityId, group, price, raw.title(), risky, now);
        if (change != ListingRepository.Change.NEW) {
            group = repo.storedGroup(raw.token()).orElse(group); // may know more from an earlier description read
        }
        return risky ? Optional.empty() : Optional.of(new Candidate(raw, group, price, change));
    }

    /**
     * Second look at a candidate: read the full description, re-classify registry / part number
     * (the description usually says more than the title), re-check against the right group,
     * then rules → LLM. Empty = rejected.
     */
    private Optional<Deal> review(Candidate c, Deal deal, StatsCache cache) {
        String token = c.raw().token();
        String description = divar.fetchDescription(token);
        String fullText = c.raw().title() + "\n" + description;

        RiskRules.Verdict v = risk.check(fullText);
        if (v.rejected()) {
            log.info("Rejected {} ({}) by rules: {}", token, c.group().label(), v.hard());
            repo.markRisky(token);
            return Optional.empty();
        }

        Registry reg = registryRegion.registry(fullText);
        Region region = registryRegion.region(c.group().spec().model(), fullText);
        MarketGroup g = c.group();
        MarketGroup refined = new MarketGroup(g.spec(), g.condition(),
                reg == Registry.UNKNOWN ? g.registry() : reg,
                region == Region.UNKNOWN ? g.region() : region);
        if (!refined.equals(g)) {
            repo.updateClassification(token, refined.registry(), refined.region());
            Candidate rc = new Candidate(c.raw(), refined, c.price(), c.change());
            DealEvaluator.Result again = decide(rc, cache);
            if (again.deal().isEmpty() || !huntable(refined)) {
                log.info("{}: description says {} → compared with its own group, not a deal ({})",
                        token, refined.label(), again.outcome());
                return Optional.empty();
            }
            deal = again.deal().get();
        }

        List<String> warnings = new ArrayList<>(v.soft());
        if (deal.coarseStats()) {
            warnings.add("آگهی‌های این پارت کم بود؛ با میانه‌ی همه‌ی پارت‌ها مقایسه شد");
        }
        if (deal.group().registry() == Registry.UNKNOWN) {
            warnings.add("وضعیت رجیستری در آگهی ذکر نشده");
        }

        Optional<LlmAdvisor.Review> ai = llm.review(deal, description);
        if (ai.isPresent() && ai.get().skip()) {
            log.info("Rejected {} ({}) by LLM: {}", token, deal.group().label(), ai.get().reason());
            repo.markRisky(token);
            return Optional.empty();
        }
        return Optional.of(deal.withReview(warnings, ai.map(LlmAdvisor.Review::reason).orElse(null)));
    }
}
