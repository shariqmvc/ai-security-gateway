package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;

public record RoutingDecision(
        Provider provider,
        String model,
        RoutingStrategy strategy,
        RoutingDecisionMetadata metadata) {

    public RoutingDecision(
            Provider provider,
            String model,
            RoutingStrategy strategy) {
        this(provider, model, strategy, RoutingDecisionMetadata.empty());
    }
}
