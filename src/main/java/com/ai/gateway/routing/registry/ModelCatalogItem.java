package com.ai.gateway.routing.registry;

import com.ai.gateway.enums.Provider;

import java.util.Set;

public record ModelCatalogItem(
        Provider provider,
        String modelId,
        String displayName,
        Set<String> capabilities,
        boolean enabled) {

    public static ModelCatalogItem from(ModelDefinition definition) {
        return new ModelCatalogItem(
                definition.provider(),
                definition.modelId(),
                definition.displayName(),
                definition.capabilities(),
                definition.isEnabled());
    }
}
