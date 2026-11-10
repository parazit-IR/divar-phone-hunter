package ir.phonehunter.divar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ir.phonehunter.config.HttpClients;
import ir.phonehunter.config.HunterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tool: talks to Divar's web API (the same one divar.ir calls from the browser).
 * It is unofficial — if Divar changes it, only this class and {@link DivarResponseParser} need fixing.
 * Requests are paced and retried politely.
 */
@Component
public class DivarClient {

    private static final Logger log = LoggerFactory.getLogger(DivarClient.class);
    private static final int MAX_ATTEMPTS = 3;

    private final HunterProperties props;
    private final ObjectMapper mapper;
    private final DivarResponseParser parser;
    private final HttpClient http;
    private long lastRequestAt = 0;

    public DivarClient(HunterProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.parser = new DivarResponseParser(mapper);
        this.http = HttpClients.create(props.getDivar());
    }

    public DivarResponseParser.Page search(String cityId, String category, String query, JsonNode pagination)
            throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.putArray("city_ids").add(cityId);
        ObjectNode searchData = body.putObject("search_data");
        ObjectNode data = searchData.putObject("form_data").putObject("data");
        data.putObject("category").putObject("str").put("value", category);
        String sort = props.getDivar().getSort();
        if (sort != null && !sort.isBlank()) {
            data.putObject("sort").putObject("str").put("value", sort);
        }
        if (query != null && !query.isBlank()) {
            searchData.put("query", toPersianQuery(query));
        }
        if (pagination != null && !pagination.isNull()) {
            body.set("pagination_data", pagination);
        }
        HttpRequest req = base(props.getDivar().getBaseUrl() + "/v8/postlist/w/search")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        try {
            return parser.parseSearch(send(req));
        } catch (IOException | InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not parse Divar search response: " + e.getMessage(), e);
        }
    }

    /** Persian brand names return far more Divar results than Latin ones. */
    static String toPersianQuery(String q) {
        return q.replaceAll("(?i)\\biphone\\b", "آیفون")
                .replaceAll("(?i)\\bsamsung\\b", "سامسونگ")
                .replaceAll("(?i)\\bgalaxy\\b", "گلکسی")
                .trim();
    }

    public String fetchDescription(String token) {
        try {
            HttpRequest req = base(props.getDivar().getBaseUrl() + "/v8/posts-v2/web/" + token).GET().build();
            return parser.parseDescription(send(req));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception e) {
            log.warn("Could not fetch description of {}: {}", token, e.getMessage());
            return "";
        }
    }

    private HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", props.getDivar().getUserAgent())
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                .header("Origin", "https://divar.ir")
                .header("Referer", "https://divar.ir/");
    }

    private synchronized String send(HttpRequest req) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            pace();
            HttpResponse<String> res;
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                res = null;
                last = e;
            }
            if (res != null) {
                int code = res.statusCode();
                if (code / 100 == 2) {
                    return res.body();
                }
                last = new IOException("Divar HTTP " + code + " for " + req.uri());
                if (code != 429 && code < 500) {
                    throw last; // 4xx other than rate-limit: retrying will not help
                }
            }
            if (attempt == MAX_ATTEMPTS) {
                break;
            }
            long backoff = 3_000L * attempt * attempt;
            log.warn("Divar request failed (attempt {}/{}): {} — waiting {} ms", attempt, MAX_ATTEMPTS, last.getMessage(), backoff);
            Thread.sleep(backoff);
        }
        throw last;
    }

    private void pace() throws InterruptedException {
        long delay = props.getPoll().getRequestDelayMs();
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, delay / 2));
        long wait = lastRequestAt + delay + jitter - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
