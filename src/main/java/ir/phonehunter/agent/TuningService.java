package ir.phonehunter.agent;

import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.ThresholdTuner;
import ir.phonehunter.config.HunterProperties;
import ir.phonehunter.notify.MessageFormatter;
import ir.phonehunter.notify.TelegramNotifier;
import ir.phonehunter.store.ListingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Threshold tuning from real multi-day data: on demand (/api/tuning) and as a nightly Telegram report
 * that tells you which min-discount-percent would give your target number of deals per day.
 */
@Component
public class TuningService {

    private final HunterProperties props;
    private final ListingRepository repo;
    private final ThresholdTuner tuner;
    private final DealEvaluator evaluator;
    private final TelegramNotifier telegram;

    public TuningService(HunterProperties props, ListingRepository repo, ThresholdTuner tuner,
                         DealEvaluator evaluator, TelegramNotifier telegram) {
        this.props = props;
        this.repo = repo;
        this.tuner = tuner;
        this.evaluator = evaluator;
        this.telegram = telegram;
    }

    public ThresholdTuner.Report report() {
        long now = System.currentTimeMillis();
        long since = now - Duration.ofDays(props.getStats().getWindowDays()).toMillis();
        return tuner.tune(repo.observations(props.getCity().getId(), since), evaluator.rules(),
                props.getTuning().getTargetDealsPerDay(), now);
    }

    @Scheduled(cron = "${hunter.tuning.report-cron:0 0 22 * * *}", zone = "${hunter.tuning.zone:Asia/Tehran}")
    public void dailyReport() {
        if (props.getTuning().isDailyReport()) {
            telegram.send(format(report()));
        }
    }

    String format(ThresholdTuner.Report r) {
        long dayAgo = System.currentTimeMillis() - Duration.ofDays(1).toMillis();
        StringBuilder sb = new StringBuilder("📋 <b>گزارش روزانه‌ی بازار — ")
                .append(MessageFormatter.esc(props.getCity().getName())).append("</b>\n\n");
        sb.append("آگهی جدید در ۲۴ ساعت: ").append(repo.newSince(props.getCity().getId(), dayAgo)).append('\n');
        sb.append("شکار اعلام‌شده در ۲۴ ساعت: ").append(repo.notifiedSince(dayAgo)).append("\n\n");
        sb.append("داده: ").append(r.daysOfData()).append(" روز، ").append(r.adsAnalysed()).append(" آگهی\n");
        sb.append("با آستانه‌ی فعلی ").append(r.currentMinDiscount()).append("٪ ≈ ")
                .append(r.dealsPerDayFixed()).append(" شکار در روز\n");
        sb.append("با حالت تطبیقی ≈ ").append(r.dealsPerDayAdaptive()).append(" شکار در روز\n");
        sb.append("پیشنهاد: <b>min-discount-percent=").append((int) r.recommendedMinDiscount()).append("</b>\n\n");
        sb.append("<b>پرآگهی‌ترین گروه‌ها:</b>\n");
        r.groups().stream().limit(8).forEach(g -> sb.append("• ").append(MessageFormatter.esc(g.group()))
                .append(" — میانه ").append(MessageFormatter.toman(g.median()))
                .append("، n=").append(g.samples())
                .append("، پراکندگی ").append(g.spreadPercent()).append("٪\n"));
        r.notes().forEach(n -> sb.append("\nℹ️ ").append(MessageFormatter.esc(n)));
        return sb.toString();
    }
}
