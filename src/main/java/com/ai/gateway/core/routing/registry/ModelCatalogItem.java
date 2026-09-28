package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.cost.config.PricingConfig;
import com.ai.gateway.core.cost.dto.ModelPricing;
import com.ai.gateway.core.model.Provider;

import java.math.BigDecimal;
import java.util.Set;

public record ModelCatalogItem(
        Provider provider,
        String modelId,
        String displayName,
        Set<String> capabilities,
        boolean enabled,
        int contextWindowTokens,
        BigDecimal inputPricePerMillionTokens,
        BigDecimal outputPricePerMillionTokens) {

    public static ModelCatalogItem from(ModelDefinition definition, PricingConfig pricingConfig) {
        ModelPricing pricing = pricingConfig.getPricing(definition.provider(), definition.modelId());
        return new ModelCatalogItem(
                definition.provider(),
                definition.modelId(),
                definition.displayName(),
                definition.capabilities(),
                definition.isEnabled(),
                definition.contextWindowTokens(),
                pricing.getInputPricePerMillionTokens(),
                pricing.getOutputPricePerMillionTokens());
    }
}
