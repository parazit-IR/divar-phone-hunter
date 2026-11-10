package ir.phonehunter.divar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.RawListing;
import ir.phonehunter.util.PriceParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Divar's (unofficial) web API JSON. Written defensively: Divar changes field names from
 * time to time, so we look for a token + title and classify the description lines by content
 * (price vs. condition) instead of trusting their position.
 */
public class DivarResponseParser {

    public record Page(List<RawListing> listings, boolean hasNext, JsonNode nextPagination) {
    }

    private final ObjectMapper mapper;

    public DivarResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Page parseSearch(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        List<RawListing> out = new ArrayList<>();
        JsonNode widgets = root.path("list_widgets");
        if (!widgets.isArray()) {
            widgets = root.path("web_widgets").path("post_list");
        }
        for (JsonNode w : widgets) {
            if (!"POST_ROW".equals(w.path("widget_type").asText())) {
                continue;
            }
            JsonNode d = w.path("data");
            String token = firstNonBlank(
                    d.path("action").path("payload").path("token").asText(null),
                    d.path("token").asText(null));
            String title = d.path("title").asText("");
            if (token == null || title.isBlank()) {
                continue;
            }
            String price = null;
            String condition = null;
            String subtitle = d.path("bottom_description_text").asText("");
            for (String key : List.of("middle_description_text", "top_description_text", "bottom_description_text")) {
                String v = d.path(key).asText("");
                if (price == null && PriceParser.looksLikePrice(v)) {
                    price = v;
                } else if (condition == null && Condition.isConditionText(v)) {
                    condition = v;
                }
            }
            out.add(new RawListing(token, title.trim(), price, condition, subtitle));
        }
        JsonNode pagination = root.path("pagination");
        boolean hasNext = pagination.path("has_next_page").asBoolean(false);
        JsonNode next = pagination.path("data");
        return new Page(out, hasNext && !next.isMissingNode(), next.isMissingNode() ? null : next);
    }

    /** Pulls the free-text description out of a post-detail response. */
    public String parseDescription(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        StringBuilder sb = new StringBuilder();
        collectDescriptions(root, sb);
        return sb.toString().trim();
    }

    private void collectDescriptions(JsonNode node, StringBuilder sb) {
        if (node.isObject()) {
            if ("DESCRIPTION_ROW".equals(node.path("widget_type").asText())) {
                sb.append(node.path("data").path("text").asText("")).append('\n');
                return;
            }
            node.fields().forEachRemaining(e -> collectDescriptions(e.getValue(), sb));
        } else if (node.isArray()) {
            node.forEach(n -> collectDescriptions(n, sb));
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return (b != null && !b.isBlank()) ? b : null;
    }
}
