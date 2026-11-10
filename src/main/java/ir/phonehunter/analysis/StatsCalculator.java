package ir.phonehunter.analysis;

import ir.phonehunter.model.PriceStats;

import java.util.List;
import java.util.Optional;

/**
 * Median and percentiles that ignore junk prices (1 toman, 999,999,999, deposit-only prices...).
 * First pass finds a rough median, second pass keeps only prices in [40% , 250%] of it.
 */
public class StatsCalculator {

    private static final double LOW_CUT = 0.40;
    private static final double HIGH_CUT = 2.50;

    public Optional<PriceStats> compute(List<Long> prices) {
        List<Long> sorted = prices.stream().filter(p -> p != null && p > 0).sorted().toList();
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        double rough = percentile(sorted, 0.5);
        List<Long> clean = sorted.stream()
                .filter(p -> p >= rough * LOW_CUT && p <= rough * HIGH_CUT)
                .toList();
        if (clean.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new PriceStats(
                clean.size(),
                Math.round(percentile(clean, 0.25)),
                Math.round(percentile(clean, 0.50)),
                Math.round(percentile(clean, 0.75))));
    }

    /** Linear interpolation between closest ranks; {@code sorted} must be ascending. */
    static double percentile(List<Long> sorted, double q) {
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double pos = q * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return sorted.get(lo) + (sorted.get(hi) - sorted.get(lo)) * (pos - lo);
    }
}
