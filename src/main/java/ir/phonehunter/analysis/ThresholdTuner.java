package ir.phonehunter.analysis;

import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tunes thresholds from the agent's own multi-day history:
 * replays every ad seen in the window through the deal rules (fixed and adaptive) and reports
 * how many deals each setting would have produced per day, per group and overall.
 * Note: the replay uses the whole window's median, so it is slightly optimistic (look-ahead).
 */
public class ThresholdTuner {

    public record Observation(MarketGroup group, long price, long firstSeen) {
    }

    public record GroupReport(String group, int samples, long p25, long median, long p75, double spreadPercent,
                              double adaptiveThreshold, int dealsFixed, int dealsAdaptive) {
    }

    public record SweepPoint(double minDiscountPercent, double dealsPerDay) {
    }

    public record Report(double daysOfData, int adsAnalysed, double currentMinDiscount,
                         double dealsPerDayFixed, double dealsPerDayAdaptive,
                         double recommendedMinDiscount, double targetDealsPerDay,
                         List<SweepPoint> sweep, List<GroupReport> groups, List<String> notes) {
    }

    private static final long DAY = 86_400_000L;

    private final StatsCalculator calc;

    public ThresholdTuner(StatsCalculator calc) {
        this.calc = calc;
    }

    public Report tune(List<Observation> obs, DealEvaluator.Rules current, double targetDealsPerDay, long now) {
        List<String> notes = new ArrayList<>();
        if (obs.isEmpty()) {
            return new Report(0, 0, current.minDiscountPercent(), 0, 0, current.minDiscountPercent(),
                    targetDealsPerDay, List.of(), List.of(), List.of("هنوز داده‌ای جمع نشده است."));
        }
        long oldest = obs.stream().mapToLong(Observation::firstSeen).min().orElse(now);
        double days = Math.max(1.0, (now - oldest) / (double) DAY);

        Map<String, Optional<PriceStats>> fine = statsBy(obs, o -> o.group().fineKey());
        Map<String, Optional<PriceStats>> coarse = statsBy(obs, o -> o.group().coarseKey());

        DealEvaluator fixedEval = new DealEvaluator(current.withMinDiscount(current.minDiscountPercent()));
        DealEvaluator adaptiveEval = new DealEvaluator(current.asAdaptive());

        Map<String, int[]> perGroupDeals = new HashMap<>();
        Map<String, StatsSelector.Selected> chosen = new HashMap<>();
        int fixedTotal = 0;
        int adaptiveTotal = 0;
        RawListing dummy = new RawListing("-", "-", null, null, null);

        for (Observation o : obs) {
            Optional<StatsSelector.Selected> sel = StatsSelector.choose(o.group(), fine.get(o.group().fineKey()),
                    () -> coarse.get(o.group().coarseKey()), current.minSamples());
            if (sel.isEmpty()) {
                continue;
            }
            chosen.putIfAbsent(o.group().fineKey(), sel.get());
            int[] counts = perGroupDeals.computeIfAbsent(o.group().fineKey(), k -> new int[2]);
            if (fixedEval.evaluate(dummy, o.group(), o.price(), sel.get().stats()).outcome() == DealEvaluator.Outcome.DEAL) {
                counts[0]++;
                fixedTotal++;
            }
            if (adaptiveEval.evaluate(dummy, o.group(), o.price(), sel.get().stats()).outcome() == DealEvaluator.Outcome.DEAL) {
                counts[1]++;
                adaptiveTotal++;
            }
        }

        // sweep fixed thresholds 5% … 30%
        List<SweepPoint> sweep = new ArrayList<>();
        for (int t = 5; t <= 30; t++) {
            DealEvaluator e = new DealEvaluator(current.withMinDiscount(t));
            int n = 0;
            for (Observation o : obs) {
                StatsSelector.Selected s = chosenFor(o, fine, coarse, current.minSamples());
                if (s != null && e.evaluate(dummy, o.group(), o.price(), s.stats()).outcome() == DealEvaluator.Outcome.DEAL) {
                    n++;
                }
            }
            sweep.add(new SweepPoint(t, round1(n / days)));
        }
        // strictest threshold that still yields the target number of deals per day
        double recommended = sweep.stream()
                .filter(p -> p.dealsPerDay() >= targetDealsPerDay)
                .map(SweepPoint::minDiscountPercent)
                .max(Double::compare)
                .orElse(5.0);

        Map<String, MarketGroup> groupsByKey = new LinkedHashMap<>();
        obs.forEach(o -> groupsByKey.putIfAbsent(o.group().fineKey(), o.group()));
        List<GroupReport> groups = new ArrayList<>();
        groupsByKey.forEach((key, g) -> {
            StatsSelector.Selected s = chosen.get(key);
            Optional<PriceStats> own = fine.get(key);
            if (own.isEmpty()) {
                return;
            }
            PriceStats st = own.get();
            int[] c = perGroupDeals.getOrDefault(key, new int[2]);
            double adaptiveT = s == null ? 0 : adaptiveEval.thresholdFor(s.stats());
            groups.add(new GroupReport(g.label() + (s != null && s.coarse() ? " (میانه‌ی کلی)" : ""),
                    st.count(), st.p25(), st.median(), st.p75(), round1(DealEvaluator.spreadPercent(st)),
                    round1(adaptiveT), c[0], c[1]));
        });
        groups.sort(Comparator.comparingInt(GroupReport::samples).reversed());

        if (days < 3) {
            notes.add("داده کمتر از ۳ روز است؛ پیشنهادها هنوز قابل اعتماد نیستند.");
        }
        notes.add(String.format("برای حدود %.0f شکار در روز، min-discount-percent را %.0f بگذارید.",
                targetDealsPerDay, recommended));
        notes.add("شبیه‌سازی با میانه‌ی کل بازه انجام شده و کمی خوش‌بینانه است.");

        return new Report(round1(days), obs.size(), current.minDiscountPercent(),
                round1(fixedTotal / days), round1(adaptiveTotal / days), recommended, targetDealsPerDay,
                sweep, groups, notes);
    }

    private StatsSelector.Selected chosenFor(Observation o, Map<String, Optional<PriceStats>> fine,
                                             Map<String, Optional<PriceStats>> coarse, int minSamples) {
        return StatsSelector.choose(o.group(), fine.get(o.group().fineKey()),
                () -> coarse.get(o.group().coarseKey()), minSamples).orElse(null);
    }

    private Map<String, Optional<PriceStats>> statsBy(List<Observation> obs,
                                                      java.util.function.Function<Observation, String> key) {
        Map<String, List<Long>> prices = obs.stream().collect(Collectors.groupingBy(key,
                Collectors.mapping(Observation::price, Collectors.toList())));
        Map<String, Optional<PriceStats>> out = new HashMap<>();
        prices.forEach((k, v) -> out.put(k, calc.compute(v)));
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
