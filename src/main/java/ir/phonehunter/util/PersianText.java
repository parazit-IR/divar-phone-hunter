package ir.phonehunter.util;

import java.util.Locale;

/** Normalizes Persian/Arabic text so regex rules can work on one canonical form. */
public final class PersianText {

    private PersianText() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= '۰' && c <= '۹') {
                sb.append((char) ('0' + (c - '۰')));
            } else if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else if (c == 'ي' || c == 'ى') {
                sb.append('ی');
            } else if (c == 'ك') {
                sb.append('ک');
            } else if (c == 'أ' || c == 'إ') {
                sb.append('ا');
            } else if (c == '‌' || c == '‏' || c == '‎' || c == ' ') {
                sb.append(' ');
            } else if (c == '٬' || c == '،') {
                sb.append(',');
            } else {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
