package com.ai.gateway.routing.registry;

import com.ai.gateway.enums.Provider;

import java.util.Collections;
import java.util.Set;

public record ProviderDefinition(

        Provider provider,

        String displayName,

        ProviderStatus status,

        Set<String> capabilities

) {

    public ProviderDefinition {

        capabilities =
                capabilities == null
                        ? Collections.emptySet()
                        : Set.copyOf(capabilities);
    }

    public boolean isEnabled() {
        return status == ProviderStatus.ENABLED;
    }

    public boolean supports(String capability) {

        return capabilities.contains(capability);
    }
}
