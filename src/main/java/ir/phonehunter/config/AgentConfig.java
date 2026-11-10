package ir.phonehunter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import ir.phonehunter.analysis.DealEvaluator;
import ir.phonehunter.analysis.StatsCalculator;
import ir.phonehunter.extract.PhoneSpecExtractor;
import ir.phonehunter.extract.RiskRules;
import ir.phonehunter.llm.HttpLlmAdvisor;
import ir.phonehunter.llm.LlmAdvisor;
import ir.phonehunter.llm.NoopLlmAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the plain-Java building blocks (kept Spring-free so they are easy to unit-test). */
@Configuration
public class AgentConfig {

    @Bean
    PhoneSpecExtractor phoneSpecExtractor(HunterProperties props) {
        return new PhoneSpecExtractor(props.getAllowedModels());
    }

    @Bean
    RiskRules riskRules() {
        return new RiskRules();
    }

    @Bean
    StatsCalculator statsCalculator() {
        return new StatsCalculator();
    }

    @Bean
    DealEvaluator dealEvaluator(HunterProperties props) {
        HunterProperties.DealRules d = props.getDeal();
        return new DealEvaluator(new DealEvaluator.Rules(d.getMinDiscountPercent(), d.getMaxDiscountPercent(),
                d.getMinSamples(), d.getMinProfitToman(), d.getExtraCostToman()));
    }

    @Bean
    LlmAdvisor llmAdvisor(HunterProperties props, ObjectMapper mapper) {
        String provider = props.getLlm().getProvider();
        if (provider == null || provider.isBlank() || "none".equalsIgnoreCase(provider)) {
            return new NoopLlmAdvisor();
        }
        return new HttpLlmAdvisor(props.getLlm(), mapper);
    }
}
