package ir.phonehunter.web;

import ir.phonehunter.agent.HunterAgent;
import ir.phonehunter.agent.TuningService;
import ir.phonehunter.analysis.ThresholdTuner;
import ir.phonehunter.store.ListingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Small JSON API to look inside the agent: market medians, tuning, recent deals, run-now. */
@RestController
@RequestMapping("/api")
public class StatusController {

    private final ListingRepository repo;
    private final HunterAgent agent;
    private final TuningService tuning;

    public StatusController(ListingRepository repo, HunterAgent agent, TuningService tuning) {
        this.repo = repo;
        this.agent = agent;
        this.tuning = tuning;
    }

    /** Median / P25 / P75 / spread of every market group (model + storage + condition + registry + part). */
    @GetMapping("/market")
    public List<ThresholdTuner.GroupReport> market() {
        return tuning.report().groups();
    }

    /** Full threshold-tuning report: deals/day per setting, sweep 5%–30%, recommendation. */
    @GetMapping("/tuning")
    public ThresholdTuner.Report tuning() {
        return tuning.report();
    }

    @GetMapping("/deals")
    public List<Map<String, Object>> deals(@RequestParam(defaultValue = "50") int limit) {
        return repo.recentDeals(limit);
    }

    @PostMapping("/run")
    public Map<String, Object> runNow() {
        return agent.runOnce();
    }

    @PostMapping("/report")
    public Map<String, Object> sendReportNow() {
        tuning.dailyReport();
        return Map.of("status", "sent");
    }
}
