package ir.phonehunter.web;

import ir.phonehunter.agent.HunterAgent;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.config.HunterProperties;
import ir.phonehunter.model.Condition;
import ir.phonehunter.model.PhoneSpec;
import ir.phonehunter.store.ListingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON API to look inside the agent: market medians, recent deals, run-now. */
@RestController
@RequestMapping("/api")
public class StatusController {

    private final HunterProperties props;
    private final ListingRepository repo;
    private final StatsCalculator stats;
    private final HunterAgent agent;

    public StatusController(HunterProperties props, ListingRepository repo, StatsCalculator stats, HunterAgent agent) {
        this.props = props;
        this.repo = repo;
        this.stats = stats;
        this.agent = agent;
    }

    @GetMapping("/market")
    public List<Map<String, Object>> market() {
        String city = props.getCity().getId();
        long since = System.currentTimeMillis() - Duration.ofDays(props.getStats().getWindowDays()).toMillis();
        return repo.groups(city, since).stream().map(g -> {
            PhoneSpec spec = new PhoneSpec(g.model(), g.storageGb());
            Condition cond = Condition.valueOf(g.condition());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phone", spec.label());
            row.put("condition", cond.persian());
            stats.compute(repo.prices(city, spec, cond, since)).ifPresent(s -> {
                row.put("count", s.count());
                row.put("p25", s.p25());
                row.put("median", s.median());
                row.put("p75", s.p75());
            });
            return row;
        }).toList();
    }

    @GetMapping("/deals")
    public List<Map<String, Object>> deals(@RequestParam(defaultValue = "50") int limit) {
        return repo.recentDeals(limit);
    }

    @PostMapping("/run")
    public Map<String, Object> runNow() {
        return agent.runOnce();
    }
}
