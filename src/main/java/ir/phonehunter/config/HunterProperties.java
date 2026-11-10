package ir.phonehunter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** Everything configurable from application.properties under the "hunter." prefix. */
@ConfigurationProperties(prefix = "hunter")
public class HunterProperties {

    private City city = new City();
    private String category = "mobile-phones";
    private List<String> queries = new ArrayList<>();
    private List<String> allowedModels = new ArrayList<>();
    private Poll poll = new Poll();
    private Stats stats = new Stats();
    private DealRules deal = new DealRules();
    private Divar divar = new Divar();
    private Telegram telegram = new Telegram();
    private Llm llm = new Llm();

    public static class City {
        private String id = "1";
        private String name = "تهران";

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    public static class Poll {
        private int pagesPerQuery = 2;
        private int bootstrapPages = 8;
        private long requestDelayMs = 1500;

        public int getPagesPerQuery() { return pagesPerQuery; }
        public void setPagesPerQuery(int v) { this.pagesPerQuery = v; }
        public int getBootstrapPages() { return bootstrapPages; }
        public void setBootstrapPages(int v) { this.bootstrapPages = v; }
        public long getRequestDelayMs() { return requestDelayMs; }
        public void setRequestDelayMs(long v) { this.requestDelayMs = v; }
    }

    public static class Stats {
        private int windowDays = 10;

        public int getWindowDays() { return windowDays; }
        public void setWindowDays(int v) { this.windowDays = v; }
    }

    public static class DealRules {
        private double minDiscountPercent = 12;
        private double maxDiscountPercent = 40;
        private int minSamples = 8;
        private long minProfitToman = 2_000_000;
        private long extraCostToman = 500_000;
        private boolean notifyOnBootstrap = false;

        public double getMinDiscountPercent() { return minDiscountPercent; }
        public void setMinDiscountPercent(double v) { this.minDiscountPercent = v; }
        public double getMaxDiscountPercent() { return maxDiscountPercent; }
        public void setMaxDiscountPercent(double v) { this.maxDiscountPercent = v; }
        public int getMinSamples() { return minSamples; }
        public void setMinSamples(int v) { this.minSamples = v; }
        public long getMinProfitToman() { return minProfitToman; }
        public void setMinProfitToman(long v) { this.minProfitToman = v; }
        public long getExtraCostToman() { return extraCostToman; }
        public void setExtraCostToman(long v) { this.extraCostToman = v; }
        public boolean isNotifyOnBootstrap() { return notifyOnBootstrap; }
        public void setNotifyOnBootstrap(boolean v) { this.notifyOnBootstrap = v; }
    }

    public static class Proxy {
        private String proxyHost = "";
        private int proxyPort = 0;

        public String getProxyHost() { return proxyHost; }
        public void setProxyHost(String v) { this.proxyHost = v; }
        public int getProxyPort() { return proxyPort; }
        public void setProxyPort(int v) { this.proxyPort = v; }
    }

    public static class Divar extends Proxy {
        private String baseUrl = "https://api.divar.ir";
        private String sort = "sort_date";
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
        public String getSort() { return sort; }
        public void setSort(String v) { this.sort = v; }
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String v) { this.userAgent = v; }
    }

    public static class Telegram extends Proxy {
        private boolean enabled = false;
        private String botToken = "";
        private String chatId = "";
        private String apiUrl = "https://api.telegram.org";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public String getBotToken() { return botToken; }
        public void setBotToken(String v) { this.botToken = v; }
        public String getChatId() { return chatId; }
        public void setChatId(String v) { this.chatId = v; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String v) { this.apiUrl = v; }
    }

    public static class Llm extends Proxy {
        /** none | anthropic | openai (any OpenAI-compatible server, e.g. Ollama) */
        private String provider = "none";
        private String apiKey = "";
        private String model = "claude-haiku-5-5";
        private String baseUrl = "";

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String v) { this.baseUrl = v; }
    }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getQueries() { return queries; }
    public void setQueries(List<String> queries) { this.queries = queries; }
    public List<String> getAllowedModels() { return allowedModels; }
    public void setAllowedModels(List<String> allowedModels) { this.allowedModels = allowedModels; }
    public Poll getPoll() { return poll; }
    public void setPoll(Poll poll) { this.poll = poll; }
    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }
    public DealRules getDeal() { return deal; }
    public void setDeal(DealRules deal) { this.deal = deal; }
    public Divar getDivar() { return divar; }
    public void setDivar(Divar divar) { this.divar = divar; }
    public Telegram getTelegram() { return telegram; }
    public void setTelegram(Telegram telegram) { this.telegram = telegram; }
    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }
}
