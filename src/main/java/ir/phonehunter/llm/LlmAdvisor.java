package ir.phonehunter.llm;

import ir.phonehunter.model.Deal;

import java.util.Optional;

/**
 * The "Model" part of Agent = Model + Harness. It is only asked about the few ads that already
 * passed every cheap rule, so token cost stays tiny. Empty result = no opinion (LLM off or failed).
 */
public interface LlmAdvisor {

    record Review(boolean skip, String reason) {
    }

    Optional<Review> review(Deal deal, String description);

    String name();
}
