package ir.phonehunter.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ir.phonehunter.config.HttpClients;
import ir.phonehunter.config.HunterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Tool: sends messages to your Telegram bot. When disabled, messages are printed to the log. */
@Component
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final HunterProperties.Telegram cfg;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public TelegramNotifier(HunterProperties props, ObjectMapper mapper) {
        this.cfg = props.getTelegram();
        this.mapper = mapper;
        this.http = HttpClients.create(cfg);
    }

    public boolean send(String html) {
        if (!cfg.isEnabled() || cfg.getBotToken().isBlank() || cfg.getChatId().isBlank()) {
            log.info("[TELEGRAM DISABLED] would send:\n{}", html);
            return true;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("chat_id", cfg.getChatId());
            body.put("text", html);
            body.put("parse_mode", "HTML");
            body.put("disable_web_page_preview", false);
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create(cfg.getApiUrl() + "/bot" + cfg.getBotToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                log.warn("Telegram HTTP {}: {}", res.statusCode(), res.body());
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("Telegram send failed (VPN/proxy on?): {}", e.toString());
            return false;
        }
    }
}
