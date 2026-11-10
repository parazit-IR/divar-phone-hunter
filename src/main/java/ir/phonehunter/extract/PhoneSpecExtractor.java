package ir.phonehunter.extract;

import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.util.PersianText;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based (free, fast) extraction of model + storage from Persian/English ad text.
 * Only ads that rules cannot resolve are sent to the LLM, if one is configured.
 */
public class PhoneSpecExtractor {

    private static final Set<Integer> VALID_STORAGE = Set.of(32, 64, 128, 256, 512, 1024);

    private static final Pattern IPHONE = Pattern.compile(
            "iphone\\s*(1[0-9])(?!\\d)\\s*(promax|pro|mini|plus)?");
    private static final Pattern GALAXY_S = Pattern.compile(
            "(?<![a-z0-9])s\\s*(2[0-9])(?!\\d)\\s*(ultra|plus|fe)?");
    private static final Pattern GALAXY_A = Pattern.compile(
            "(?<![a-z0-9])a\\s*(\\d{2})(?!\\d)(s(?![a-z]))?");

    private static final Pattern STORAGE_UNIT = Pattern.compile(
            "(?<!\\d)(\\d{2,4})\\s*(?:gb|gig|g|گیگابایت|گیگ|گیک)(?![a-z])");
    private static final Pattern STORAGE_TB = Pattern.compile(
            "(?<!\\d)([12])\\s*(?:tb|ترابایت|ترا|tera)");
    private static final Pattern STORAGE_SLASH = Pattern.compile(
            "(?<!\\d)(32|64|128|256|512)\\s*/\\s*\\d{1,2}(?!\\d)|(?<!\\d)\\d{1,2}\\s*/\\s*(32|64|128|256|512)(?!\\d)");
    private static final Pattern STORAGE_BARE = Pattern.compile("(?<!\\d)(64|128|256|512)(?!\\d)");

    private final List<Pattern> allowedModels;

    /** @param allowedModelRegexes regexes on the canonical model name, e.g. {@code "iPhone 1[3-5].*"} */
    public PhoneSpecExtractor(List<String> allowedModelRegexes) {
        this.allowedModels = allowedModelRegexes.stream().map(Pattern::compile).toList();
    }

    public Optional<PhoneSpec> extract(String title, String extraText) {
        String text = canonical(title + " " + (extraText == null ? "" : extraText));
        Optional<String> model = extractModel(canonical(title)).or(() -> extractModel(text));
        if (model.isEmpty() || !isAllowed(model.get())) {
            return Optional.empty();
        }
        Integer storage = extractStorage(canonical(title));
        if (storage == null) {
            storage = extractStorage(text);
        }
        return storage == null ? Optional.empty() : Optional.of(new PhoneSpec(model.get(), storage));
    }

    /** Model only (used to decide whether an ad is in scope even when storage is unclear). */
    public Optional<String> extractModelOnly(String title) {
        return extractModel(canonical(title)).filter(this::isAllowed);
    }

    public boolean isAllowed(String model) {
        return allowedModels.stream().anyMatch(p -> p.matcher(model).matches());
    }

    static String canonical(String raw) {
        String t = " " + PersianText.normalize(raw) + " ";
        t = t.replaceAll("پرو\\s*مکس|pro\\s*max", " promax ");
        t = t.replaceAll("آیفون|ایفون|آیفن|ای فون|iphone|apple iphone", " iphone ");
        t = t.replaceAll("سامسونگ|سامسونک|samsung", " samsung ");
        t = t.replaceAll("گلکسی|گالاکسی|galaxy", " galaxy ");
        t = t.replaceAll("اولترا|الترا|آلترا|ultra", " ultra ");
        t = t.replaceAll("پلاس|plus|\\+", " plus ");
        t = t.replaceAll("مینی|mini", " mini ");
        t = t.replaceAll("پرو(?!\\p{L})|pro(?![a-z])", " pro ");
        t = t.replaceAll("اف\\s*ای(?!\\p{L})|(?<![a-z])fe(?![a-z])", " fe ");
        t = t.replaceAll("(?<!\\p{L})اس(?=\\s*\\d)", " s");
        t = t.replaceAll("(?<!\\p{L})ای(?=\\s*\\d)", " a");
        return t.replaceAll("\\s+", " ").trim();
    }

    private Optional<String> extractModel(String t) {
        Matcher m = IPHONE.matcher(t);
        if (m.find()) {
            return Optional.of("iPhone " + m.group(1) + suffix(m.group(2)));
        }
        boolean samsung = t.contains("samsung") || t.contains("galaxy");
        if (!samsung) {
            return Optional.empty();
        }
        m = GALAXY_S.matcher(t);
        if (m.find()) {
            return Optional.of("Galaxy S" + m.group(1) + suffix(m.group(2)));
        }
        m = GALAXY_A.matcher(t);
        if (m.find()) {
            return Optional.of("Galaxy A" + m.group(1) + (m.group(2) != null ? "s" : ""));
        }
        return Optional.empty();
    }

    private static String suffix(String s) {
        if (s == null) {
            return "";
        }
        return switch (s) {
            case "promax" -> " Pro Max";
            case "pro" -> " Pro";
            case "mini" -> " Mini";
            case "plus" -> " Plus";
            case "ultra" -> " Ultra";
            case "fe" -> " FE";
            default -> "";
        };
    }

    static Integer extractStorage(String t) {
        Set<Integer> explicit = new LinkedHashSet<>();
        Matcher m = STORAGE_UNIT.matcher(t);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (VALID_STORAGE.contains(v)) {
                explicit.add(v);
            }
        }
        m = STORAGE_TB.matcher(t);
        while (m.find()) {
            explicit.add(1024 * Integer.parseInt(m.group(1)));
        }
        m = STORAGE_SLASH.matcher(t);
        while (m.find()) {
            explicit.add(Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2)));
        }
        explicit.removeIf(v -> !VALID_STORAGE.contains(v));
        if (explicit.size() == 1) {
            return explicit.iterator().next();
        }
        if (explicit.size() > 1) {
            return null; // e.g. "128 و 256 موجود است" — ambiguous, do not guess
        }
        Set<Integer> bare = new LinkedHashSet<>();
        m = STORAGE_BARE.matcher(t);
        while (m.find()) {
            bare.add(Integer.parseInt(m.group(1)));
        }
        return bare.size() == 1 ? bare.iterator().next() : null;
    }
}
