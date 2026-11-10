package ir.phonehunter.notify;

import ir.phonehunter.model.Deal;

/** Builds the Telegram (HTML parse mode) message for a deal. */
public final class MessageFormatter {

    private MessageFormatter() {
    }

    public static String format(Deal d, String cityName) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>شکار جدید — ").append(esc(d.spec().label())).append("</b>\n");
        sb.append("وضعیت: ").append(d.condition().persian()).append(" | شهر: ").append(esc(cityName)).append("\n\n");
        sb.append("💰 قیمت آگهی: <b>").append(toman(d.price())).append("</b>\n");
        sb.append("📊 میانه: ").append(toman(d.stats().median()))
                .append(" (<b>").append(d.discountPercent()).append("٪</b> زیر میانه)\n");
        sb.append("📈 قیمت فروش هدف (صدک ۷۵): ").append(toman(d.stats().p75())).append("\n");
        sb.append("💵 سود تخمینی: <b>").append(toman(d.estimatedProfit())).append("</b>\n");
        sb.append("🧮 تعداد نمونه: ").append(d.stats().count()).append(" آگهی\n\n");
        sb.append("📝 ").append(esc(d.listing().title())).append("\n");
        if (d.warnings() != null && !d.warnings().isEmpty()) {
            sb.append("⚠️ ").append(esc(String.join(" | ", d.warnings()))).append("\n");
        }
        if (d.aiNote() != null && !d.aiNote().isBlank()) {
            sb.append("🤖 ").append(esc(d.aiNote())).append("\n");
        }
        sb.append("\n🔗 ").append(d.listing().url());
        return sb.toString();
    }

    public static String toman(long v) {
        return String.format("%,d", v) + " تومان";
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
