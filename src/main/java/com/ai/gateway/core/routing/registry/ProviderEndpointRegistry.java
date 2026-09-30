package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** Resolves the executable endpoint selected by routing. */
@Service
@RequiredArgsConstructor
public class ProviderEndpointRegistry {
    private final ProviderEndpointProperties properties;

    public String requireUrl(Provider provider, String endpointId) {
        if (provider == null) throw new BusinessException("Provider is required to resolve an endpoint.");
        String id = endpointId == null || endpointId.isBlank()
                ? provider.name().toLowerCase(Locale.ROOT) + "-default"
                : endpointId;
        String url = properties.getUrls().get(id);
        if (url == null || url.isBlank()) {
            throw new BusinessException("No executable endpoint is registered for " + id + ".");
        }
        return trimTrailingSlash(url.trim());
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
