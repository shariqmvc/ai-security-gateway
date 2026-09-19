package com.ai.gateway.personal.intelligence;

import java.util.List;

public record PersonalSecurityAssessment(
        int score,
        PersonalSecurityRisk risk,
        List<String> signals) {

    /**
     * A detected prompt-injection pattern is a confirmed security signal for
     * Personal Chat and is blocked before provider invocation. Generic medium
     * risk signals (for example, a large prompt) are not automatically blocked.
     */
    public boolean promptInjectionDetected() {
        return signals != null && signals.stream()
                .anyMatch(signal -> signal != null && signal.startsWith("suspicious-pattern:"));
    }

    public boolean shouldBlock() {
        return risk == PersonalSecurityRisk.HIGH || promptInjectionDetected();
    }
}
