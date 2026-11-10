package ir.phonehunter.model;

import ir.phonehunter.util.PersianText;

/** Item condition as Divar shows it. Prices are only compared within the same condition. */
public enum Condition {
    NEW("نو"),
    LIKE_NEW("در حد نو"),
    USED("کارکرده"),
    NEEDS_REPAIR("نیاز به تعمیر"),
    UNKNOWN("نامشخص");

    private final String persian;

    Condition(String persian) {
        this.persian = persian;
    }

    public String persian() {
        return persian;
    }

    public static Condition fromDivarText(String text) {
        String n = PersianText.normalize(text);
        if (n.isEmpty()) {
            return UNKNOWN;
        }
        if (n.contains("تعمیر")) {
            return NEEDS_REPAIR;
        }
        if (n.contains("در حد نو")) {
            return LIKE_NEW;
        }
        if (n.contains("کارکرده")) {
            return USED;
        }
        if (n.matches(".*(^|\\s)نو($|\\s).*")) {
            return NEW;
        }
        return UNKNOWN;
    }

    public static boolean isConditionText(String text) {
        return fromDivarText(text) != UNKNOWN;
    }
}
