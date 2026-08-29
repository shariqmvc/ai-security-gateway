package com.ai.gateway.core.cost.service;

import com.ai.gateway.core.cost.dto.ModelPricing;
import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.dto.PreRequestCostRequest;
import com.ai.gateway.core.cost.pricing.PricingCatalog;
import com.ai.gateway.core.cost.service.impl.PreRequestCostEstimatorImpl;
import com.ai.gateway.core.model.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreRequestCostEstimatorImplTest {

    private PricingCatalog catalog(BigDecimal input, BigDecimal output) {
        PricingCatalog catalog = mock(PricingCatalog.class);
        when(catalog.getPricing(Provider.OPENAI, "gpt-test"))
                .thenReturn(ModelPricing.builder()
                        .provider(Provider.OPENAI)
                        .model("gpt-test")
                        .inputPricePerMillionTokens(input)
                        .outputPricePerMillionTokens(output)
                        .build());
        return catalog;
    }

    @Test
    void estimatesInputOutputAndTotalCost() {
        PreRequestCostEstimator estimator =
                new PreRequestCostEstimatorImpl(
                        catalog(new BigDecimal("1.00"), new BigDecimal("2.00")));

        PreRequestCostEstimate result =
                estimator.estimate(
                        PreRequestCostRequest.builder()
                                .provider(Provider.OPENAI)
                                .model("gpt-test")
                                .inputTokens(1000)
                                .outputTokens(500)
                                .build());

        assertEquals(new BigDecimal("0.001000000000"), result.getInputCost());
        assertEquals(new BigDecimal("0.001000000000"), result.getOutputCost());
        assertEquals(new BigDecimal("0.002000000000"), result.getTotalEstimatedCost());
    }

    @Test
    void usesNormalInputPriceWhenCachedPriceIsUnknown() {
        PreRequestCostEstimator estimator =
                new PreRequestCostEstimatorImpl(
                        catalog(new BigDecimal("1.00"), new BigDecimal("2.00")));

        PreRequestCostEstimate result =
                estimator.estimate(
                        PreRequestCostRequest.builder()
                                .provider(Provider.OPENAI)
                                .model("gpt-test")
                                .inputTokens(1000)
                                .cachedInputTokens(400)
                                .outputTokens(0)
                                .build());

        assertEquals(new BigDecimal("0.000600000000"), result.getInputCost());
        assertEquals(new BigDecimal("0.000400000000"), result.getCachedInputCost());
        assertEquals(new BigDecimal("0.001000000000"), result.getTotalEstimatedCost());
    }

    @Test
    void supportsKnownCachedInputPrice() {
        PricingCatalog catalog = mock(PricingCatalog.class);
        when(catalog.getPricing(Provider.OPENAI, "gpt-test"))
                .thenReturn(ModelPricing.builder()
                        .provider(Provider.OPENAI)
                        .model("gpt-test")
                        .inputPricePerMillionTokens(new BigDecimal("1.00"))
                        .cachedInputPricePerMillionTokens(new BigDecimal("0.10"))
                        .outputPricePerMillionTokens(new BigDecimal("2.00"))
                        .build());

        PreRequestCostEstimator estimator =
                new PreRequestCostEstimatorImpl(catalog);

        PreRequestCostEstimate result =
                estimator.estimate(
                        PreRequestCostRequest.builder()
                                .provider(Provider.OPENAI)
                                .model("gpt-test")
                                .inputTokens(1000)
                                .cachedInputTokens(400)
                                .outputTokens(500)
                                .build());

        assertEquals(new BigDecimal("0.000040000000"), result.getCachedInputCost());
        assertEquals(new BigDecimal("0.001640000000"), result.getTotalEstimatedCost());
    }

    @Test
    void rejectsCachedTokensGreaterThanInputTokens() {
        PreRequestCostEstimator estimator =
                new PreRequestCostEstimatorImpl(
                        catalog(new BigDecimal("1.00"), new BigDecimal("2.00")));

        assertThrows(
                IllegalArgumentException.class,
                () -> estimator.estimate(
                        PreRequestCostRequest.builder()
                                .provider(Provider.OPENAI)
                                .model("gpt-test")
                                .inputTokens(100)
                                .cachedInputTokens(101)
                                .build()));
    }

    @Test
    void includesExplicitAdditionalEstimatedCost() {
        PreRequestCostEstimator estimator =
                new PreRequestCostEstimatorImpl(
                        catalog(new BigDecimal("1.00"), new BigDecimal("2.00")));

        PreRequestCostEstimate result =
                estimator.estimate(
                        PreRequestCostRequest.builder()
                                .provider(Provider.OPENAI)
                                .model("gpt-test")
                                .inputTokens(1000)
                                .outputTokens(500)
                                .additionalEstimatedCost(new BigDecimal("0.010000"))
                                .build());

        assertEquals(
                new BigDecimal("0.012000000000"),
                result.getTotalEstimatedCost());
    }
}
