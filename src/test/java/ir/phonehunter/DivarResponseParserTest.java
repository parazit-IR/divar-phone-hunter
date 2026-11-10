package ir.phonehunter;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.phonehunter.divar.DivarResponseParser;
import ir.phonehunter.model.RawListing;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DivarResponseParserTest {

    private final DivarResponseParser parser = new DivarResponseParser(new ObjectMapper());

    private String resource(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesSearchPage() throws Exception {
        DivarResponseParser.Page page = parser.parseSearch(resource("divar-search-sample.json"));
        assertEquals(2, page.listings().size());
        RawListing first = page.listings().get(0);
        assertEquals("wZabc123", first.token());
        assertEquals("آیفون ۱۳ پرو ۲۵۶ گیگ", first.title());
        assertEquals("۳۸٬۰۰۰٬۰۰۰ تومان", first.priceText());
        assertEquals("کارکرده", first.conditionText());
        assertTrue(page.hasNext());
        assertEquals("gaXyz789", page.listings().get(1).token());
    }

    @Test
    void parsesDescription() throws Exception {
        String desc = parser.parseDescription(resource("divar-post-sample.json"));
        assertTrue(desc.contains("سلامت باتری ۸۷"));
    }
}
