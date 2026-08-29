package com.ai.gateway.core.cost.pricing;

import com.ai.gateway.core.cost.dto.ModelPricing;
import com.ai.gateway.core.model.Provider;

public interface PricingCatalog {

    ModelPricing getPricing(Provider provider, String model);
}
