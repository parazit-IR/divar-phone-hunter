package ir.phonehunter.model;

/**
 * Ads are compared only inside the same market group.
 * Fine group  = model + storage + condition + registry group + region.
 * Coarse group = same without region — used as a fallback when the fine group has too few ads.
 */
public record MarketGroup(PhoneSpec spec, Condition condition, Registry registry, Region region) {

    public String fineKey() {
        return coarseKey() + "|" + region.name();
    }

    public String coarseKey() {
        return spec.model() + "|" + spec.storageGb() + "|" + condition.name() + "|" + registry.priceGroup();
    }

    public String label() {
        StringBuilder sb = new StringBuilder(spec.label()).append(" · ").append(condition.persian());
        if (registry == Registry.NOT_REGISTERED) {
            sb.append(" · ").append(registry.persian());
        }
        if (region != Region.NOT_APPLICABLE && region != Region.UNKNOWN) {
            sb.append(" · ").append(region.persian());
        }
        return sb.toString();
    }
}
