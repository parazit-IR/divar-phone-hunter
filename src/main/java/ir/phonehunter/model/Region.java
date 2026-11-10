package ir.phonehunter.model;

/**
 * iPhone part-number region family. In Iran these trade at clearly different prices:
 * CH/ZA/ZP have two physical SIM slots (premium), LL (USA) iPhone 14/15 are eSIM-only (cheaper).
 */
public enum Region {
    DUAL_SIM("پارت CH/ZA (دو سیم‌کارت)"),
    LL_US("پارت LL (آمریکا)"),
    OTHER_INTL("پارت سایر کشورها"),
    UNKNOWN("پارت نامشخص"),
    NOT_APPLICABLE("");

    private final String persian;

    Region(String persian) {
        this.persian = persian;
    }

    public String persian() {
        return persian;
    }
}
