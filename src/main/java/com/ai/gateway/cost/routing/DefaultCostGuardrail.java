package com.ai.gateway.cost.routing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DefaultCostGuardrail implements CostGuardrail {

    @Override
    public CostGuardrailDecision evaluate(
            BigDecimal estimatedCost,
            BigDecimal maximumRequestCost) {

        if (estimatedCost == null) {
            throw new IllegalArgumentException("Estimated cost is required.");
        }
        if (estimatedCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Estimated cost cannot be negative.");
        }

        if (maximumRequestCost == null) {
            return new CostGuardrailDecision(
                    true,
                    estimatedCost,
                    null,
                    "NO_REQUEST_COST_LIMIT");
        }

        boolean allowed =
                estimatedCost.compareTo(maximumRequestCost) <= 0;

        return new CostGuardrailDecision(
                allowed,
                estimatedCost,
                maximumRequestCost,
                allowed
                        ? "WITHIN_REQUEST_COST_LIMIT"
                        : "REQUEST_COST_LIMIT_EXCEEDED");
    }
}
