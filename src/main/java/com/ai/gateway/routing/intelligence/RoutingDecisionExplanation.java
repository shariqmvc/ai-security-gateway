package com.ai.gateway.routing.intelligence;

import java.util.List;

public record RoutingDecisionExplanation(
        String summary,
        List<String> appliedSignals,
        List<String> rejectedConstraints,
        List<String> capabilityRequirements) {

    public RoutingDecisionExplanation {
        appliedSignals = appliedSignals == null ? List.of() : List.copyOf(appliedSignals);
        rejectedConstraints = rejectedConstraints == null ? List.of() : List.copyOf(rejectedConstraints);
        capabilityRequirements = capabilityRequirements == null ? List.of() : List.copyOf(capabilityRequirements);
    }
}
