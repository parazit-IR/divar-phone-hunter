package ir.phonehunter.config;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Each external service gets its own HttpClient so it can have its own proxy.
 * Typical setup in Iran: Divar direct (Iranian IP), Telegram/LLM through a local VPN HTTP proxy.
 */
public final class HttpClients {

    private HttpClients() {
    }

    public static HttpClient create(HunterProperties.Proxy proxy) {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (proxy.getProxyHost() != null && !proxy.getProxyHost().isBlank() && proxy.getProxyPort() > 0) {
            b.proxy(ProxySelector.of(new InetSocketAddress(proxy.getProxyHost(), proxy.getProxyPort())));
        } else {
            b.proxy(HttpClient.Builder.NO_PROXY);
        }
        return b.build();
    }
}
