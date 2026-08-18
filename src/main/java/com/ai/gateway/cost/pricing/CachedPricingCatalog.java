package com.ai.gateway.cost.pricing;

import com.ai.gateway.cost.config.PricingConfig;
import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.enums.Provider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CachedPricingCatalog implements PricingCatalog {

    private record PricingKey(Provider provider, String model) {}

    private final PricingConfig pricingConfig;
    private final Map<PricingKey, ModelPricing> cache = new ConcurrentHashMap<>();

    public CachedPricingCatalog(PricingConfig pricingConfig) {
        this.pricingConfig = pricingConfig;
    }

    @Override
    public ModelPricing getPricing(Provider provider, String model) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model is required.");
        }

        return cache.computeIfAbsent(
                new PricingKey(provider, model),
                key -> pricingConfig.getPricing(key.provider(), key.model()));
    }

    public int cachedEntryCount() {
        return cache.size();
    }
}
