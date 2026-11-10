package ir.phonehunter.util;

/** Turns Divar price text such as "۴۸٬۵۰۰٬۰۰۰ تومان" into a number (toman). */
public final class PriceParser {

    private PriceParser() {
    }

    /** @return price in toman, or {@code null} for "توافقی" / missing / unparseable prices. */
    public static Long parseToman(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String n = PersianText.normalize(text);
        if (n.contains("توافقی") || n.contains("مجانی") || n.contains("رایگان")) {
            return null;
        }
        String digits = n.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 13) {
            return null;
        }
        long value = Long.parseLong(digits);
        if (n.contains("میلیون") && value < 10_000) {
            value *= 1_000_000L;
        }
        return value > 0 ? value : null;
    }

    public static boolean looksLikePrice(String text) {
        if (text == null) {
            return false;
        }
        String n = PersianText.normalize(text);
        return n.contains("تومان") || n.contains("توافقی");
    }
}
