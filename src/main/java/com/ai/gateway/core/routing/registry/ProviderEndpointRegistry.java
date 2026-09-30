package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves executable endpoints and exposes endpoint candidates to routing. */
@Service
@RequiredArgsConstructor
public class ProviderEndpointRegistry {
    private final ProviderEndpointProperties properties;

    public String requireUrl(Provider provider, String endpointId) {
        ProviderEndpointProperties.Endpoint endpoint = require(provider, endpointId);
        return trimTrailingSlash(endpoint.getUrl().trim());
    }

    public List<String> endpointIds(Provider provider) {
        if (provider == null) return List.of();
        return properties.getDefinitions().entrySet().stream()
                .filter(e -> e.getValue() != null
                        && e.getValue().isEnabled()
                        && provider == e.getValue().getProvider()
                        && e.getValue().getUrl() != null
                        && !e.getValue().getUrl().isBlank())
                .map(Map.Entry::getKey)
                .toList();
    }

    public String defaultEndpointId(Provider provider) {
        return provider.name().toLowerCase(Locale.ROOT) + "-default";
    }

    private ProviderEndpointProperties.Endpoint require(Provider provider, String endpointId) {
        if (provider == null) throw new BusinessException("Provider is required to resolve an endpoint.");
        String id = endpointId == null || endpointId.isBlank() ? defaultEndpointId(provider) : endpointId;
        ProviderEndpointProperties.Endpoint endpoint = properties.getDefinitions().get(id);
        if (endpoint == null || !endpoint.isEnabled()
                || endpoint.getProvider() != provider
                || endpoint.getUrl() == null || endpoint.getUrl().isBlank()) {
            throw new BusinessException("No executable endpoint is registered for " + provider + " / " + id + ".");
        }
        return endpoint;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
