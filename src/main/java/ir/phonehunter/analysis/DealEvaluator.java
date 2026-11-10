package ir.phonehunter.analysis;

import ir.phonehunter.model.Deal;
import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;

import java.util.List;
import java.util.Optional;

/** The "decide" step of the loop: is this ad a hunt-worthy deal? */
public class DealEvaluator {

    /**
     * @param minDiscountPercent fixed mode: price must be at least this % under the median (e.g. 12)
     * @param maxDiscountPercent more than this % under the median is treated as fake/bait (e.g. 40)
     * @param minSamples         minimum ads in the group before the median is trusted
     * @param minProfitToman     resale (P75) − price − costs must be at least this
     * @param extraCostToman     your per-flip costs (commute, check-up, Divar ad fee...)
     * @param adaptive           true = per-group threshold from the group's own price spread
     * @param adaptiveFactor     threshold = factor × spread%, where spread% = (P75 − P25) / median
     * @param adaptiveFloor      adaptive threshold never goes below this %
     * @param adaptiveCeiling    adaptive threshold never goes above this %
     */
    public record Rules(double minDiscountPercent, double maxDiscountPercent, int minSamples,
                        long minProfitToman, long extraCostToman,
                        boolean adaptive, double adaptiveFactor, double adaptiveFloor, double adaptiveCeiling) {

        public static Rules fixed(double minDiscountPercent, double maxDiscountPercent, int minSamples,
                                  long minProfitToman, long extraCostToman) {
            return new Rules(minDiscountPercent, maxDiscountPercent, minSamples, minProfitToman, extraCostToman,
                    false, 0, 0, 0);
        }

        public Rules withMinDiscount(double v) {
            return new Rules(v, maxDiscountPercent, minSamples, minProfitToman, extraCostToman,
                    false, adaptiveFactor, adaptiveFloor, adaptiveCeiling);
        }

        public Rules asAdaptive() {
            return new Rules(minDiscountPercent, maxDiscountPercent, minSamples, minProfitToman, extraCostToman,
                    true, adaptiveFactor, adaptiveFloor, adaptiveCeiling);
        }
    }

    public enum Outcome { DEAL, NOT_CHEAP, TOO_FEW_SAMPLES, SUSPICIOUSLY_CHEAP, LOW_PROFIT }

    public record Result(Outcome outcome, Optional<Deal> deal) {
    }

    private final Rules rules;

    public DealEvaluator(Rules rules) {
        this.rules = rules;
    }

    public Rules rules() {
        return rules;
    }

    /** Spread of the middle half of the market, as % of the median. */
    public static double spreadPercent(PriceStats s) {
        return s.median() <= 0 ? 0 : 100.0 * (s.p75() - s.p25()) / s.median();
    }

    public double thresholdFor(PriceStats stats) {
        if (!rules.adaptive()) {
            return rules.minDiscountPercent();
        }
        double t = rules.adaptiveFactor() * spreadPercent(stats);
        return Math.max(rules.adaptiveFloor(), Math.min(rules.adaptiveCeiling(), t));
    }

    public Result evaluate(RawListing listing, MarketGroup group, long price, PriceStats stats) {
        if (stats == null || stats.count() < rules.minSamples()) {
            return new Result(Outcome.TOO_FEW_SAMPLES, Optional.empty());
        }
        double discount = 100.0 * (stats.median() - price) / stats.median();
        double threshold = thresholdFor(stats);
        if (discount < threshold) {
            return new Result(Outcome.NOT_CHEAP, Optional.empty());
        }
        if (discount > rules.maxDiscountPercent()) {
            return new Result(Outcome.SUSPICIOUSLY_CHEAP, Optional.empty());
        }
        long profit = stats.p75() - price - rules.extraCostToman();
        if (profit < rules.minProfitToman()) {
            return new Result(Outcome.LOW_PROFIT, Optional.empty());
        }
        Deal deal = new Deal(listing, group, price, stats, false,
                round1(discount), round1(threshold), profit, List.of(), null);
        return new Result(Outcome.DEAL, Optional.of(deal));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
