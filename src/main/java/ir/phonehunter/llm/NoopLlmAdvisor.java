package ir.phonehunter.llm;

import ir.phonehunter.model.Deal;

import java.util.Optional;

/** Used when hunter.llm.provider=none — the agent then relies on rules only. */
public class NoopLlmAdvisor implements LlmAdvisor {

    @Override
    public Optional<Review> review(Deal deal, String description) {
        return Optional.empty();
    }

    @Override
    public String name() {
        return "none (rules only)";
    }
}
