package com.ai.gateway.cost.routing;

import java.math.BigDecimal;
import java.util.Objects;

public record CostGuardrailDecision(
        boolean allowed,
        BigDecimal estimatedCost,
        BigDecimal maximumRequestCost,
        String reason) {

    public CostGuardrailDecision {
        estimatedCost = Objects.requireNonNull(estimatedCost, "Estimated cost is required.");
        if (estimatedCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Estimated cost cannot be negative.");
        }
        if (maximumRequestCost != null
                && maximumRequestCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Maximum request cost cannot be negative.");
        }
        reason = reason == null ? "" : reason;
    }
}
