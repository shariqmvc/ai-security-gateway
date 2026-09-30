package com.ai.gateway.core.routing;

import com.ai.gateway.core.model.Provider;

/**
 * Canonical executable provider endpoint metadata used by the routing domain.
 * The actual provider adapter remains responsible for making the network call.
 */
public record ProviderEndpoint(
        Provider provider,
        String endpointId,
        String baseUrl,
        boolean enabled) {

    public ProviderEndpoint {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("Endpoint id is required.");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Endpoint base URL is required.");
        }
    }
}
