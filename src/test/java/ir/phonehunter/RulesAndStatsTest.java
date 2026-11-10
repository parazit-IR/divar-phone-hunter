package ir.phonehunter;

import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.extract.RiskRules;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.model.PriceStats;
import ir.phonehunter.model.RawListing;
import ir.phonehunter.util.PriceParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RulesAndStatsTest {

    @Test
    void prices() {
        assertEquals(Long.valueOf(48_500_000L), PriceParser.parseToman("۴۸٬۵۰۰٬۰۰۰ تومان"));
        assertEquals(Long.valueOf(48_500_000L), PriceParser.parseToman("48,500,000 تومان"));
        assertNull(PriceParser.parseToman("توافقی"));
    }

    @Test
    void conditions() {
        assertEquals(Condition.NEW, Condition.fromDivarText("نو"));
        assertEquals(Condition.LIKE_NEW, Condition.fromDivarText("در حد نو"));
        assertEquals(Condition.USED, Condition.fromDivarText("کارکرده"));
        assertEquals(Condition.NEEDS_REPAIR, Condition.fromDivarText("نیاز به تعمیر"));
    }

    @Test
    void risk() {
        RiskRules r = new RiskRules();
        assertTrue(r.check("آیفون ۱۳ پرو نات اکتیو").rejected());
        assertTrue(r.check("آیفون ۱۴ ریجستر نشده").rejected());
        assertTrue(r.check("سامسونگ s23 های کپی").rejected());
        assertTrue(r.check("فقط معاوضه با ۱۵ پرو").rejected());
        assertFalse(r.check("آیفون ۱۳ پرو رجیستر شده، سلامت باتری ۸۹").rejected());
        RiskRules.Verdict v = r.check("آیفون ۱۴ پرو باتری ۷۸٪ معاوضه هم میکنم");
        assertFalse(v.rejected());
        assertTrue(v.soft().contains("سلامت باتری 78٪"));
        assertTrue(v.soft().contains("پیشنهاد معاوضه"));
    }

    @Test
    void statsIgnoreJunkPrices() {
        StatsCalculator calc = new StatsCalculator();
        PriceStats s = calc.compute(List.of(1L, 40_000_000L, 42_000_000L, 44_000_000L, 46_000_000L,
                48_000_000L, 999_999_999L)).orElseThrow();
        assertEquals(5, s.count());
        assertEquals(44_000_000L, s.median());
        assertEquals(46_000_000L, s.p75());
    }

    @Test
    void dealDecision() {
        DealEvaluator ev = new DealEvaluator(new DealEvaluator.Rules(12, 40, 8, 2_000_000, 500_000));
        RawListing raw = new RawListing("abc", "t", "", "", "");
        PhoneSpec spec = new PhoneSpec("iPhone 13 Pro", 256);
        PriceStats stats = new PriceStats(20, 42_000_000, 45_000_000, 47_000_000);

        assertEquals(DealEvaluator.Outcome.DEAL, ev.evaluate(raw, spec, Condition.USED, 38_000_000, stats).outcome());
        assertEquals(DealEvaluator.Outcome.NOT_CHEAP, ev.evaluate(raw, spec, Condition.USED, 43_000_000, stats).outcome());
        assertEquals(DealEvaluator.Outcome.SUSPICIOUSLY_CHEAP, ev.evaluate(raw, spec, Condition.USED, 20_000_000, stats).outcome());
        assertEquals(DealEvaluator.Outcome.TOO_FEW_SAMPLES,
                ev.evaluate(raw, spec, Condition.USED, 38_000_000, new PriceStats(3, 1, 45_000_000, 47_000_000)).outcome());
        // profit = P75 47M - price 38M - extra cost 0.5M
        assertEquals(8_500_000L, ev.evaluate(raw, spec, Condition.USED, 38_000_000, stats).deal().orElseThrow().estimatedProfit());
    }
}
