package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;

import java.util.Collections;
import java.util.Set;

public record ModelDefinition(

        Provider provider,

        String modelId,

        String displayName,

        ModelStatus status,

        Set<String> capabilities,

        int contextWindowTokens

) {

    public ModelDefinition(
            Provider provider,
            String modelId,
            String displayName,
            ModelStatus status,
            Set<String> capabilities) {
        this(provider, modelId, displayName, status, capabilities, 16000);
    }

    public ModelDefinition {

        capabilities =
                capabilities == null
                        ? Collections.emptySet()
                        : Set.copyOf(capabilities);
    }

    public boolean isEnabled() {
        return status == ModelStatus.ENABLED;
    }

    public boolean supports(String capability) {

        return capabilities.contains(capability);
    }
}
