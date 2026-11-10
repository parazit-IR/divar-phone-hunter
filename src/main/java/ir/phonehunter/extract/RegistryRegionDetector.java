package ir.phonehunter.extract;

import ir.phonehunter.model.Region;
import ir.phonehunter.model.Registry;
import ir.phonehunter.util.PersianText;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects registry status and iPhone part-number region from ad text. */
public class RegistryRegionDetector {

    private static final String REG = "(?:ریجستر|رجیستر|ریجیستر|رجستر|ریجستری|رجیستری|ریجیستری)";

    private static final Pattern NOT_REGISTERED = Pattern.compile(
            REG + "\\s*(?:نشده|نیست|ندارد|نداره|نمی\\s*شه|نمیشه|نشده است)"
                    + "|بدون\\s*" + REG
                    + "|نات\\s*اکتیو|not\\s*active|notactive|غیر\\s*" + REG
                    + "|پلمپ\\s*گمرک|" + REG + "\\s*نکرده");
    private static final Pattern REGISTERED = Pattern.compile(
            REG + "\\s*(?:شده|دار|داره|دارد|فعال|اوکی|ok|اصلی)"
                    + "|(?<!\\p{L})(?:registered|reg)(?![a-z])"
                    + "|" + REG + "\\s*(?:با\\s*)?(?:گارانتی|کارت)");

    /** "ch/a", "za/a", "ll/a", "j/a", "aa/a"... — the strongest signal. */
    private static final Pattern PART_SLASH = Pattern.compile("(?<![a-z])([a-z]{1,2})\\s*/\\s*a(?![a-z])");
    /** Bare region tokens such as "پارت ch" or "ll" next to model text. */
    private static final Pattern PART_BARE = Pattern.compile("(?<![a-z])(ch|za|zp|ll)(?![a-z])");

    private static final Map<String, Region> PART_CODES = Map.ofEntries(
            Map.entry("ch", Region.DUAL_SIM), Map.entry("za", Region.DUAL_SIM), Map.entry("zp", Region.DUAL_SIM),
            Map.entry("ll", Region.LL_US),
            Map.entry("aa", Region.OTHER_INTL), Map.entry("ae", Region.OTHER_INTL), Map.entry("hn", Region.OTHER_INTL),
            Map.entry("vn", Region.OTHER_INTL), Map.entry("kh", Region.OTHER_INTL), Map.entry("j", Region.OTHER_INTL),
            Map.entry("b", Region.OTHER_INTL), Map.entry("d", Region.OTHER_INTL), Map.entry("f", Region.OTHER_INTL),
            Map.entry("y", Region.OTHER_INTL), Map.entry("t", Region.OTHER_INTL), Map.entry("x", Region.OTHER_INTL),
            Map.entry("c", Region.OTHER_INTL), Map.entry("ta", Region.OTHER_INTL), Map.entry("tu", Region.OTHER_INTL),
            Map.entry("ru", Region.OTHER_INTL), Map.entry("rk", Region.OTHER_INTL), Map.entry("qn", Region.OTHER_INTL),
            Map.entry("ah", Region.OTHER_INTL), Map.entry("th", Region.OTHER_INTL), Map.entry("zd", Region.OTHER_INTL),
            Map.entry("cl", Region.OTHER_INTL), Map.entry("ty", Region.OTHER_INTL), Map.entry("le", Region.OTHER_INTL));

    private static final Pattern FA_DUAL = Pattern.compile(
            "سی\\s*اچ|زد\\s*آ|زد\\s*ای|نسخه\\s*چین|ورژن\\s*چین|ریجن\\s*چین|پارت\\s*چین|هنگ\\s*کنگ|سنگاپور"
                    + "|دو\\s*سیم|دوسیم|2\\s*سیم|دو\\s*سیمکارت|دو\\s*سیم\\s*کارت|dual\\s*sim");
    private static final Pattern FA_LL = Pattern.compile(
            "ال\\s*ال(?!\\p{L})|نسخه\\s*(?:آمریکا|امریکا)|ورژن\\s*(?:آمریکا|امریکا)|پارت\\s*(?:آمریکا|امریکا)|ریجن\\s*(?:آمریکا|امریکا)"
                    + "|فقط\\s*(?:ای\\s*سیم|esim)|بدون\\s*(?:جای\\s*)?سیم\\s*کارت\\s*فیزیکی|esim\\s*only");

    public Registry registry(String text) {
        String n = PersianText.normalize(text);
        if (NOT_REGISTERED.matcher(n).find()) {
            return Registry.NOT_REGISTERED;
        }
        if (REGISTERED.matcher(n).find()) {
            return Registry.REGISTERED;
        }
        return Registry.UNKNOWN;
    }

    /** @param model canonical model name; region only matters for iPhones */
    public Region region(String model, String text) {
        if (model == null || !model.startsWith("iPhone")) {
            return Region.NOT_APPLICABLE;
        }
        String n = PersianText.normalize(text);
        Set<Region> found = EnumSet.noneOf(Region.class);
        Matcher m = PART_SLASH.matcher(n);
        while (m.find()) {
            Region r = PART_CODES.get(m.group(1));
            if (r != null) {
                found.add(r);
            }
        }
        if (found.isEmpty()) {
            m = PART_BARE.matcher(n);
            while (m.find()) {
                found.add(PART_CODES.get(m.group(1)));
            }
            if (FA_DUAL.matcher(n).find()) {
                found.add(Region.DUAL_SIM);
            }
            if (FA_LL.matcher(n).find()) {
                found.add(Region.LL_US);
            }
        }
        return found.size() == 1 ? found.iterator().next() : Region.UNKNOWN;
    }
}
