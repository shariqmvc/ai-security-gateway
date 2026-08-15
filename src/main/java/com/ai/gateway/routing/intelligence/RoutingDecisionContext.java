package com.ai.gateway.routing.intelligence;

import com.ai.gateway.enums.Provider;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record RoutingDecisionContext(
        UUID tenantId,
        String tenantCode,
        Provider defaultProvider,
        String defaultModel,
        Set<String> requiredCapabilities,
        boolean extensiveResearchRequested,
        boolean extensiveResearchEnabled,
        String executionRole,
        RoutingPriority routingPriority) {

    public RoutingDecisionContext {
        requiredCapabilities = requiredCapabilities == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(requiredCapabilities));
        routingPriority = routingPriority == null
                ? RoutingPriority.BALANCED
                : routingPriority;
    }
}
