package com.ai.gateway.core.routing;

import com.ai.gateway.core.model.Provider;

import java.util.List;
import java.util.Set;

/**
 * Provider-level routing controls. This is deliberately independent of the
 * provider adapter so policy, routing and execution remain separate concerns.
 */
public record ProviderPreferences(
        List<Provider> orderedProviders,
        Set<Provider> excludedProviders,
        boolean allowFallbacks) {

    public ProviderPreferences {
        orderedProviders = orderedProviders == null ? List.of() : List.copyOf(orderedProviders);
        excludedProviders = excludedProviders == null ? Set.of() : Set.copyOf(excludedProviders);
    }

    public static ProviderPreferences defaults() {
        return new ProviderPreferences(List.of(), Set.of(), true);
    }

    public boolean excludes(Provider provider) {
        return provider != null && excludedProviders.contains(provider);
    }
}
