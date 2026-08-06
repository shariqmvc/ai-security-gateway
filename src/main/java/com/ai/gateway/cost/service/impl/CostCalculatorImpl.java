package com.ai.gateway.cost.service.impl;

import com.ai.gateway.cost.config.PricingConfig;
import com.ai.gateway.cost.dto.CostRequest;
import com.ai.gateway.cost.dto.CostResponse;
import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.cost.service.CostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class CostCalculatorImpl implements CostCalculator {

    private static final BigDecimal ONE_MILLION =
            new BigDecimal("1000000");

    private final PricingConfig pricingConfig;

    @Override
    public CostResponse calculate(CostRequest request) {

        ModelPricing pricing =
                pricingConfig.getPricing(
                        request.getProvider(),
                        request.getModel());

        BigDecimal inputCost =
                calculateCost(
                        request.getInputTokens(),
                        pricing.getInputPricePerMillionTokens());

        BigDecimal outputCost =
                calculateCost(
                        request.getOutputTokens(),
                        pricing.getOutputPricePerMillionTokens());

        BigDecimal totalCost =
                inputCost.add(outputCost);

        return CostResponse.builder()
                .inputCost(inputCost)
                .outputCost(outputCost)
                .totalCost(totalCost)
                .build();
    }

    private BigDecimal calculateCost(
            Integer tokens,
            BigDecimal pricePerMillionTokens) {

        if (tokens == null || tokens == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(tokens)
                .multiply(pricePerMillionTokens)
                .divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
    }
}