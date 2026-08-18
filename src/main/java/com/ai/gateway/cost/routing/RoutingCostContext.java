package com.ai.gateway.cost.routing;

import java.math.BigDecimal;

public record RoutingCostContext(
        BigDecimal maximumRequestCost,
        BigDecimal remainingWorkflowBudget) {

    public RoutingCostContext {
        if (maximumRequestCost != null
                && maximumRequestCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Maximum request cost cannot be negative.");
        }
        if (remainingWorkflowBudget != null
                && remainingWorkflowBudget.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Remaining workflow budget cannot be negative.");
        }
    }

    public static RoutingCostContext requestOnly(
            BigDecimal maximumRequestCost) {
        return new RoutingCostContext(maximumRequestCost, null);
    }

    /**
     * The workflow budget is contextual input supplied by the orchestration
     * or budget layer. This class does not account for or mutate workflow spend.
     */
    public BigDecimal effectiveMaximumCost() {
        if (maximumRequestCost == null) {
            return remainingWorkflowBudget;
        }
        if (remainingWorkflowBudget == null) {
            return maximumRequestCost;
        }
        return maximumRequestCost.min(remainingWorkflowBudget);
    }
}
