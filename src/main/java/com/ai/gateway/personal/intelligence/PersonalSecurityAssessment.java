package com.ai.gateway.personal.intelligence;

import java.util.List;

public record PersonalSecurityAssessment(
        int score,
        PersonalSecurityRisk risk,
        List<String> signals,
        List<String> labels,
        String decision,
        String model,
        String modelVersion,
        long latencyMs) {

    public PersonalSecurityAssessment(
            int score,
            PersonalSecurityRisk risk,
            List<String> signals) {
        this(score, risk, signals, List.of(),
                risk == PersonalSecurityRisk.HIGH ? "BLOCK" : "ALLOW",
                "local-deterministic", "legacy", 0L);
    }

    public PersonalSecurityAssessment {
        signals = signals == null ? List.of() : List.copyOf(signals);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * Prompt injection is considered a confirmed security category when the
     * ML firewall reports it or the legacy deterministic detector reports a
     * suspicious pattern.
     */
    public boolean promptInjectionDetected() {
        return labels.stream().anyMatch(label ->
                        "PROMPT_INJECTION".equalsIgnoreCase(label)
                                || "JAILBREAK".equalsIgnoreCase(label))
                || signals.stream().anyMatch(signal ->
                        signal != null && signal.startsWith("suspicious-pattern:"));
    }

    public boolean shouldBlock() {
        return "BLOCK".equalsIgnoreCase(decision)
                || risk == PersonalSecurityRisk.HIGH
                || promptInjectionDetected();
    }
}
