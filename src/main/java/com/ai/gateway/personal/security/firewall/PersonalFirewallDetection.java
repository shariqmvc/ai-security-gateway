package com.ai.gateway.personal.security.firewall;

import java.util.List;

/** Immutable result returned by the external AIRouter Firewall service. */
public record PersonalFirewallDetection(
        String requestId,
        String decision,
        String risk,
        double score,
        List<String> labels,
        List<String> signals,
        String model,
        String modelVersion,
        double latencyMs) {

    public PersonalFirewallDetection {
        labels = labels == null ? List.of() : List.copyOf(labels);
        signals = signals == null ? List.of() : List.copyOf(signals);
    }

    public boolean blocked() {
        return "BLOCK".equalsIgnoreCase(decision);
    }
}
