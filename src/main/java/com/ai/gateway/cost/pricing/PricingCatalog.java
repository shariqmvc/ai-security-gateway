package com.ai.gateway.cost.pricing;

import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.enums.Provider;

public interface PricingCatalog {

    ModelPricing getPricing(Provider provider, String model);
}
