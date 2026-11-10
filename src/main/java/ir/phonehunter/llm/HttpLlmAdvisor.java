package ir.phonehunter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ir.phonehunter.config.HttpClients;
import ir.phonehunter.config.HunterProperties;
import ir.phonehunter.model.Deal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * LLM reviewer over plain HTTP. Supports the Anthropic Messages API and any OpenAI-compatible
 * /chat/completions server (OpenAI, Ollama, LM Studio, OpenRouter, ...).
 */
public class HttpLlmAdvisor implements LlmAdvisor {

    private static final Logger log = LoggerFactory.getLogger(HttpLlmAdvisor.class);

    static final String SYSTEM_PROMPT = """
            You review second-hand phone ads from Divar (Iran) for a reseller who buys under-priced phones and resells them.
            The ad already looks cheap versus the city median. Decide if it is worth a phone call.
            Skip it if the text suggests: fake/copy device, not registered (ریجستر/رجیستر نشده, نات اکتیو), iCloud/Apple ID lock,
            parts-only, swap-only, installment/deposit tricks, serious damage or replaced screen/battery that kills resale value,
            the price is a bait/placeholder, or the model/storage in the text does not match what we detected.
            Respond with ONLY a JSON object: {"skip": true|false, "reason": "<short Persian sentence, max 20 words>"}
            """;

    private final HunterProperties.Llm cfg;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final boolean anthropic;

    public HttpLlmAdvisor(HunterProperties.Llm cfg, ObjectMapper mapper) {
        this.cfg = cfg;
        this.mapper = mapper;
        this.http = HttpClients.create(cfg);
        this.anthropic = "anthropic".equalsIgnoreCase(cfg.getProvider());
    }

    @Override
    public String name() {
        return cfg.getProvider() + ":" + cfg.getModel();
    }

    @Override
    public Optional<Review> review(Deal deal, String description) {
        String user = """
                Detected: %s | condition: %s
                Price: %,d toman | city median: %,d | P75: %,d | samples: %d
                Title: %s
                Description:
                %s
                """.formatted(deal.spec().label(), deal.condition().persian(), deal.price(),
                deal.stats().median(), deal.stats().p75(), deal.stats().count(),
                deal.listing().title(), description == null || description.isBlank() ? "(none)" : description);
        try {
            String text = anthropic ? callAnthropic(user) : callOpenAi(user);
            int s = text.indexOf('{');
            int e = text.lastIndexOf('}');
            if (s < 0 || e <= s) {
                log.warn("LLM returned no JSON: {}", text);
                return Optional.empty();
            }
            JsonNode json = mapper.readTree(text.substring(s, e + 1));
            return Optional.of(new Review(json.path("skip").asBoolean(false), json.path("reason").asText("")));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("LLM review failed ({}): {}", name(), ex.getMessage());
            return Optional.empty();
        }
    }

    private String callAnthropic(String user) throws Exception {
        String base = cfg.getBaseUrl().isBlank() ? "https://api.anthropic.com" : cfg.getBaseUrl();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("max_tokens", 300);
        body.put("system", SYSTEM_PROMPT);
        ObjectNode msg = body.putArray("messages").addObject();
        msg.put("role", "user");
        msg.put("content", user);
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/messages"))
                .timeout(Duration.ofSeconds(60))
                .header("x-api-key", cfg.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        JsonNode res = mapper.readTree(send(req));
        StringBuilder sb = new StringBuilder();
        res.path("content").forEach(c -> sb.append(c.path("text").asText("")));
        return sb.toString();
    }

    private String callOpenAi(String user) throws Exception {
        String base = cfg.getBaseUrl().isBlank() ? "https://api.openai.com/v1" : cfg.getBaseUrl();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("temperature", 0);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
        messages.addObject().put("role", "user").put("content", user);
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(90))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!cfg.getApiKey().isBlank()) {
            rb.header("Authorization", "Bearer " + cfg.getApiKey());
        }
        JsonNode res = mapper.readTree(send(rb.build()));
        return res.path("choices").path(0).path("message").path("content").asText("");
    }

    private String send(HttpRequest req) throws Exception {
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
        }
        return res.body();
    }
}
