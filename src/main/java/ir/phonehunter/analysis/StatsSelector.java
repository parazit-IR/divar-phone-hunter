package ir.phonehunter.analysis;

import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.Region;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Picks which median to compare with: the fine group (with part-number region) when it has enough
 * ads, otherwise the coarse group (all regions). LL (USA) iPhones are never compared with the mixed
 * market because they are structurally cheaper — that would create fake "deals".
 */
public final class StatsSelector {

    public record Selected(PriceStats stats, boolean coarse) {
    }

    private StatsSelector() {
    }

    public static Optional<Selected> choose(MarketGroup group, Optional<PriceStats> fine,
                                            Supplier<Optional<PriceStats>> coarse, int minSamples) {
        if (fine.isPresent() && fine.get().count() >= minSamples) {
            return Optional.of(new Selected(fine.get(), false));
        }
        if (group.region() != Region.LL_US && group.region() != Region.NOT_APPLICABLE) {
            Optional<PriceStats> c = coarse.get();
            if (c.isPresent() && c.get().count() >= minSamples) {
                return Optional.of(new Selected(c.get(), true));
            }
        }
        return fine.map(s -> new Selected(s, false));
    }
}
