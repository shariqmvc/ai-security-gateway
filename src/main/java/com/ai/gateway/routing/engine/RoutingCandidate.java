package com.ai.gateway.routing.engine;

import com.ai.gateway.enums.Provider;

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