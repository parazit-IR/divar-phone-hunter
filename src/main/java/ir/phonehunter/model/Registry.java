package ir.phonehunter.model;

/** Iranian customs registration (رجیستری). Not-registered phones are a separate, cheaper market. */
public enum Registry {
    REGISTERED("رجیستر شده"),
    NOT_REGISTERED("رجیستر نشده"),
    UNKNOWN("رجیستری نامشخص");

    private final String persian;

    Registry(String persian) {
        this.persian = persian;
    }

    public String persian() {
        return persian;
    }

    /** Price group: unknown is treated like registered (most sellers only mention it when it is NOT registered). */
    public String priceGroup() {
        return this == NOT_REGISTERED ? "NR" : "OK";
    }
}
