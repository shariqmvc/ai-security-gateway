package com.ai.gateway.cost.pricing;

import com.ai.gateway.cost.config.PricingConfig;
import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.enums.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedPricingCatalogTest {

    @Test
    void cachesPricingLookupByProviderAndModel() {
        PricingConfig config = mock(PricingConfig.class);
        ModelPricing pricing = ModelPricing.builder()
                .provider(Provider.OPENAI)
                .model("gpt-test")
                .inputPricePerMillionTokens(new BigDecimal("1.00"))
                .outputPricePerMillionTokens(new BigDecimal("2.00"))
                .build();

        when(config.getPricing(Provider.OPENAI, "gpt-test"))
                .thenReturn(pricing);

        CachedPricingCatalog catalog =
                new CachedPricingCatalog(config);

        assertSame(
                pricing,
                catalog.getPricing(Provider.OPENAI, "gpt-test"));
        assertSame(
                pricing,
                catalog.getPricing(Provider.OPENAI, "gpt-test"));

        verify(config, times(1))
                .getPricing(Provider.OPENAI, "gpt-test");
        assertEquals(1, catalog.cachedEntryCount());
    }

    @Test
    void rejectsInvalidLookup() {
        CachedPricingCatalog catalog =
                new CachedPricingCatalog(mock(PricingConfig.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.getPricing(null, "model"));

        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.getPricing(Provider.OPENAI, " "));
    }
}
