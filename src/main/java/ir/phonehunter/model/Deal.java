package ir.phonehunter.model;

import java.util.List;

/** A listing the agent decided is worth the user's attention. */
public record Deal(
        RawListing listing,
        MarketGroup group,
        long price,
        PriceStats stats,
        boolean coarseStats,
        double discountPercent,
        double thresholdPercent,
        long estimatedProfit,
        List<String> warnings,
        String aiNote
) {
    public PhoneSpec spec() {
        return group.spec();
    }

    public Condition condition() {
        return group.condition();
    }

    public Deal withStatsSource(boolean coarse) {
        return new Deal(listing, group, price, stats, coarse, discountPercent, thresholdPercent, estimatedProfit,
                warnings, aiNote);
    }

    public Deal withReview(List<String> newWarnings, String note) {
        return new Deal(listing, group, price, stats, coarseStats, discountPercent, thresholdPercent, estimatedProfit,
                newWarnings, note);
    }
}
