package ir.phonehunter.model;

import java.util.List;

/** A listing the agent decided is worth the user's attention. */
public record Deal(
        RawListing listing,
        PhoneSpec spec,
        Condition condition,
        long price,
        PriceStats stats,
        double discountPercent,
        long estimatedProfit,
        List<String> warnings,
        String aiNote
) {
    public Deal withReview(List<String> newWarnings, String note) {
        return new Deal(listing, spec, condition, price, stats, discountPercent, estimatedProfit, newWarnings, note);
    }
}
