package com.ai.gateway.cost.service.impl;

import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.cost.dto.PreRequestCostRequest;
import com.ai.gateway.cost.pricing.PricingCatalog;
import com.ai.gateway.cost.service.PreRequestCostEstimator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PreRequestCostEstimatorImpl implements PreRequestCostEstimator {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final int SCALE = 12;

    private final PricingCatalog pricingCatalog;

    @Override
    public PreRequestCostEstimate estimate(PreRequestCostRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Cost request is required.");
        }

        request.validate();

        ModelPricing pricing =
                pricingCatalog.getPricing(
                        request.getProvider(),
                        request.getModel());

        int inputTokens = valueOrZero(request.getInputTokens());
        int cachedInputTokens = valueOrZero(request.getCachedInputTokens());
        int billableInputTokens = inputTokens - cachedInputTokens;

        BigDecimal inputCost = tokenCost(
                billableInputTokens,
                pricing.getInputPricePerMillionTokens());

        BigDecimal cachedInputCost = cachedInputCost(
                cachedInputTokens,
                pricing);

        BigDecimal outputCost = tokenCost(
                valueOrZero(request.getOutputTokens()),
                pricing.getOutputPricePerMillionTokens());

        BigDecimal additionalCost =
                request.getAdditionalEstimatedCost() == null
                        ? BigDecimal.ZERO
                        : request.getAdditionalEstimatedCost();

        BigDecimal total =
                inputCost
                        .add(cachedInputCost)
                        .add(outputCost)
                        .add(additionalCost);

        return PreRequestCostEstimate.builder()
                .provider(request.getProvider())
                .model(request.getModel())
                .inputCost(inputCost)
                .cachedInputCost(cachedInputCost)
                .outputCost(outputCost)
                .additionalEstimatedCost(additionalCost)
                .totalEstimatedCost(total)
                .build();
    }

    private BigDecimal cachedInputCost(
            int cachedTokens,
            ModelPricing pricing) {

        if (cachedTokens == 0) {
            return BigDecimal.ZERO;
        }

        /*
         * A null cached-input price means the pricing catalog does not
         * provide a known discounted cached-input price. In that case,
         * conservatively use the normal input price rather than inventing
         * a discount.
         */
        BigDecimal cachedPrice =
                pricing.getCachedInputPricePerMillionTokens() == null
                        ? pricing.getInputPricePerMillionTokens()
                        : pricing.getCachedInputPricePerMillionTokens();

        return tokenCost(cachedTokens, cachedPrice);
    }

    private BigDecimal tokenCost(
            int tokens,
            BigDecimal pricePerMillionTokens) {

        if (tokens == 0 || pricePerMillionTokens == null) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(tokens)
                .multiply(pricePerMillionTokens)
                .divide(ONE_MILLION, SCALE, RoundingMode.HALF_UP);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
