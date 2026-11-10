package ir.phonehunter.extract;

import ir.phonehunter.util.PersianText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keyword rules that catch the usual reasons an ad is "too cheap".
 * HARD → the ad is dropped (and excluded from median). SOFT → shown as a warning in Telegram.
 */
public class RiskRules {

    public record Verdict(List<String> hard, List<String> soft) {
        public boolean rejected() {
            return !hard.isEmpty();
        }
    }

    private static final Map<Pattern, String> HARD = new LinkedHashMap<>();
    private static final Map<Pattern, String> SOFT = new LinkedHashMap<>();
    private static final Pattern BATTERY = Pattern.compile("(?:سلامت\\s*)?باتری\\s*[:٪%]?\\s*(\\d{2,3})\\s*(?:%|٪|درصد)?");

    static {
        HARD.put(p("فقط\\s*(معاوضه|تعویض|تهاتر)"), "فقط معاوضه");
        HARD.put(p("قطعه\\s*ای|برای\\s*قطعه|بابت\\s*قطعه|جهت\\s*قطعه"), "فروش قطعه‌ای");
        // Not-registered phones are NOT rejected here: they form their own price group (see RegistryRegionDetector).
        HARD.put(p("های\\s*کپی|ماستر\\s*کپی|فول\\s*کپی|(?<!\\p{L})کپی(?!\\p{L})|طرح\\s*اصلی|فیک|hi\\s*copy|high\\s*copy|master\\s*copy|replica"),
                "کپی / غیراصل");
        HARD.put(p("کارتن\\s*خالی|جعبه\\s*خالی|فقط\\s*(کارتن|جعبه)"), "فقط جعبه");
        HARD.put(p("(?<!\\p{L})(اجاره|خریداریم|خریدار\\s*هستیم|خریدار\\s*گوشی)(?!\\p{L})"), "آگهی خرید/اجاره، نه فروش");

        SOFT.put(p("خراب|سوخته|شکسته|(?<!\\p{L})ترک(?!\\p{L})|ضربه\\s*خورده|نیاز\\s*به\\s*تعمیر"), "اشاره به خرابی/ضربه");
        SOFT.put(p("تعویض\\s*شده|عوض\\s*شده|تعویضی|(?<!\\p{L})فابریک\\s*نیست"), "قطعه تعویض‌شده");
        SOFT.put(p("آیکلود|ایکلود|icloud|اپل\\s*آیدی|apple\\s*id"), "اشاره به آیکلود/اپل‌آیدی — حضوری چک شود");
        SOFT.put(p("قسط|اقساط|پیش\\s*پرداخت|(?<!\\p{L})چک(?!\\p{L})"), "فروش قسطی / شرایطی");
        SOFT.put(p("(?<!\\p{L})معاوضه(?!\\p{L})"), "پیشنهاد معاوضه");
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    public Verdict check(String text) {
        String n = PersianText.normalize(text);
        List<String> hard = new ArrayList<>();
        List<String> soft = new ArrayList<>();
        HARD.forEach((pattern, label) -> {
            if (pattern.matcher(n).find()) {
                hard.add(label);
            }
        });
        SOFT.forEach((pattern, label) -> {
            if (pattern.matcher(n).find()) {
                soft.add(label);
            }
        });
        Matcher b = BATTERY.matcher(n);
        if (b.find()) {
            int health = Integer.parseInt(b.group(1));
            if (health >= 50 && health < 85) {
                soft.add("سلامت باتری " + health + "٪");
            }
        }
        if (!hard.isEmpty()) {
            soft.remove("پیشنهاد معاوضه");
        }
        return new Verdict(List.copyOf(hard), List.copyOf(soft));
    }
}
