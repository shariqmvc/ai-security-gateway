package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.model.Provider;

public record RoutingCandidate(
        Provider provider,
        String model) {

    public RoutingCandidate {
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Provider is required.");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException(
                    "Model is required.");
        }
    }
}