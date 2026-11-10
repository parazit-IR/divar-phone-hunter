package ir.phonehunter.analysis;

import ir.phonehunter.model.Condition;
import ir.phonehunter.model.Deal;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;

import java.util.List;
import java.util.Optional;

/** The "decide" step of the loop: is this ad a hunt-worthy deal? */
public class DealEvaluator {

    /**
     * @param minDiscountPercent price must be at least this % under the median (e.g. 12)
     * @param maxDiscountPercent more than this % under the median is treated as fake/bait (e.g. 40)
     * @param minSamples         minimum ads in the group before the median is trusted
     * @param minProfitToman     resale (P75) − price − costs must be at least this
     * @param extraCostToman     your per-flip costs (commute, check-up, Divar ad fee...)
     */
    public record Rules(double minDiscountPercent, double maxDiscountPercent, int minSamples,
                        long minProfitToman, long extraCostToman) {
    }

    public enum Outcome { DEAL, NOT_CHEAP, TOO_FEW_SAMPLES, SUSPICIOUSLY_CHEAP, LOW_PROFIT }

    public record Result(Outcome outcome, Optional<Deal> deal) {
    }

    private final Rules rules;

    public DealEvaluator(Rules rules) {
        this.rules = rules;
    }

    public Result evaluate(RawListing listing, PhoneSpec spec, Condition condition, long price, PriceStats stats) {
        if (stats == null || stats.count() < rules.minSamples()) {
            return new Result(Outcome.TOO_FEW_SAMPLES, Optional.empty());
        }
        double discount = 100.0 * (stats.median() - price) / stats.median();
        if (discount < rules.minDiscountPercent()) {
            return new Result(Outcome.NOT_CHEAP, Optional.empty());
        }
        if (discount > rules.maxDiscountPercent()) {
            return new Result(Outcome.SUSPICIOUSLY_CHEAP, Optional.empty());
        }
        long profit = stats.p75() - price - rules.extraCostToman();
        if (profit < rules.minProfitToman()) {
            return new Result(Outcome.LOW_PROFIT, Optional.empty());
        }
        Deal deal = new Deal(listing, spec, condition, price, stats,
                Math.round(discount * 10) / 10.0, profit, List.of(), null);
        return new Result(Outcome.DEAL, Optional.of(deal));
    }
}
