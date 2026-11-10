package ir.phonehunter;

import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.analysis.StatsSelector;
import ir.phonehunter.analysis.ThresholdTuner;
import ir.phonehunter.extract.RegistryRegionDetector;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.MarketGroup;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.Region;
import ir.phonehunter.model.Registry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryRegionAndTuningTest {

    private final RegistryRegionDetector d = new RegistryRegionDetector();
    private static final String IP = "iPhone 13 Pro";

    @Test
    void registry() {
        assertEquals(Registry.REGISTERED, d.registry("آیفون ۱۳ پرو رجیستر شده"));
        assertEquals(Registry.REGISTERED, d.registry("ریجستری شده با کارت گارانتی"));
        assertEquals(Registry.NOT_REGISTERED, d.registry("آیفون ۱۴ ریجستر نشده"));
        assertEquals(Registry.NOT_REGISTERED, d.registry("ریجستری نشده"));
        assertEquals(Registry.NOT_REGISTERED, d.registry("نات اکتیو پلمپ"));
        assertEquals(Registry.NOT_REGISTERED, d.registry("بدون رجیستر"));
        assertEquals(Registry.UNKNOWN, d.registry("آیفون ۱۳ پرو ۲۵۶ تمیز"));
    }

    @Test
    void region() {
        assertEquals(Region.DUAL_SIM, d.region(IP, "پارت CH/A دو سیم"));
        assertEquals(Region.DUAL_SIM, d.region(IP, "ZA/A"));
        assertEquals(Region.DUAL_SIM, d.region(IP, "نسخه چین"));
        assertEquals(Region.LL_US, d.region(IP, "پارت LL/A"));
        assertEquals(Region.LL_US, d.region(IP, "ورژن آمریکا فقط ای سیم"));
        assertEquals(Region.OTHER_INTL, d.region(IP, "پارت نامبر AA/A امارات"));
        assertEquals(Region.UNKNOWN, d.region(IP, "آیفون ۱۳ پرو ۲۵۶ گیگ"));
        assertEquals(Region.UNKNOWN, d.region(IP, "CH/A یا LL/A موجود"));     // conflicting → don't guess
        assertEquals(Region.NOT_APPLICABLE, d.region("Galaxy S24 Ultra", "CH/A"));
    }

    @Test
    void groupsSeparateRegistryAndRegion() {
        PhoneSpec s = new PhoneSpec(IP, 256);
        MarketGroup ch = new MarketGroup(s, Condition.USED, Registry.REGISTERED, Region.DUAL_SIM);
        MarketGroup unknownReg = new MarketGroup(s, Condition.USED, Registry.UNKNOWN, Region.DUAL_SIM);
        MarketGroup nr = new MarketGroup(s, Condition.USED, Registry.NOT_REGISTERED, Region.DUAL_SIM);
        MarketGroup ll = new MarketGroup(s, Condition.USED, Registry.REGISTERED, Region.LL_US);
        assertEquals(ch.fineKey(), unknownReg.fineKey());        // unknown registry priced as registered
        assertFalse(ch.coarseKey().equals(nr.coarseKey()));     // not-registered never mixes
        assertEquals(ch.coarseKey(), ll.coarseKey());
        assertFalse(ch.fineKey().equals(ll.fineKey()));
    }

    @Test
    void selectorFallsBackExceptForLL() {
        PhoneSpec s = new PhoneSpec(IP, 256);
        PriceStats few = new PriceStats(3, 40, 45, 47);
        PriceStats many = new PriceStats(30, 40, 45, 47);
        MarketGroup unknown = new MarketGroup(s, Condition.USED, Registry.UNKNOWN, Region.UNKNOWN);
        MarketGroup ll = new MarketGroup(s, Condition.USED, Registry.UNKNOWN, Region.LL_US);

        StatsSelector.Selected a = StatsSelector.choose(unknown, Optional.of(few), () -> Optional.of(many), 8).orElseThrow();
        assertTrue(a.coarse());
        StatsSelector.Selected b = StatsSelector.choose(ll, Optional.of(few), () -> Optional.of(many), 8).orElseThrow();
        assertFalse(b.coarse());
        assertEquals(3, b.stats().count());
    }

    @Test
    void adaptiveThreshold() {
        DealEvaluator ev = new DealEvaluator(new DealEvaluator.Rules(12, 40, 8, 0, 0, true, 0.6, 7, 20));
        // tight market: spread 5% → 3% → clamped to floor 7%
        assertEquals(7.0, ev.thresholdFor(new PriceStats(20, 49_000_000, 50_000_000, 51_500_000)), 0.001);
        // volatile market: spread 30% → 18%
        assertEquals(18.0, ev.thresholdFor(new PriceStats(20, 42_500_000, 50_000_000, 57_500_000)), 0.001);
    }

    @Test
    void tunerRecommendsThresholdForTargetDealsPerDay() {
        long day = 86_400_000L;
        long now = 10 * day;
        MarketGroup g = new MarketGroup(new PhoneSpec(IP, 256), Condition.USED, Registry.REGISTERED, Region.DUAL_SIM);
        List<ThresholdTuner.Observation> obs = new ArrayList<>();
        // 10 days × 20 normal ads around 50M, plus 2 cheap ads per day (~15% and ~25% under)
        for (int dIdx = 0; dIdx < 10; dIdx++) {
            long t = dIdx * day;
            for (int i = 0; i < 20; i++) {
                obs.add(new ThresholdTuner.Observation(g, 47_000_000L + i * 300_000L, t));
            }
            obs.add(new ThresholdTuner.Observation(g, 42_000_000L, t));
            obs.add(new ThresholdTuner.Observation(g, 37_000_000L, t));
        }
        ThresholdTuner tuner = new ThresholdTuner(new StatsCalculator());
        ThresholdTuner.Report r = tuner.tune(obs, DealEvaluator.Rules.fixed(12, 40, 8, 2_000_000, 500_000), 2, now);

        assertEquals(10.0, r.daysOfData(), 0.001);
        assertEquals(2.0, r.dealsPerDayFixed(), 0.001);              // both cheap ads per day
        assertEquals(15.0, r.recommendedMinDiscount(), 0.001);        // median 49.55M: 42M is 15.2% under → 15% is the strictest giving 2/day
        assertEquals(1, r.groups().size());
        assertEquals(220, r.groups().get(0).samples());
    }
}
