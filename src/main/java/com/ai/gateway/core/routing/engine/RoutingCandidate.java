package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.model.Provider;

public record RoutingCandidate(
        Provider provider,
        String model,
        String endpointId) {

    public RoutingCandidate(
            Provider provider,
            String model) {
        this(provider, model, null);
    }

    public RoutingCandidate {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model is required.");
        }
    }

    /** Stable provider/model identity for routing metadata and diagnostics. */
    public String candidateKey() {
        return provider.name() + "/" + model
                + (endpointId == null || endpointId.isBlank() ? "" : "@" + endpointId);
    }
}
