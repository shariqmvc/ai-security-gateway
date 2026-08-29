package com.ai.gateway.core.cost.routing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class RoutingCostContextTest {

    @Test
    void usesRequestLimitWhenNoWorkflowBudgetExists() {
        RoutingCostContext context =
                RoutingCostContext.requestOnly(
                        new BigDecimal("0.50"));

        assertEquals(
                new BigDecimal("0.50"),
                context.effectiveMaximumCost());
    }

    @Test
    void usesLowerOfRequestLimitAndWorkflowBudget() {
        RoutingCostContext context =
                new RoutingCostContext(
                        new BigDecimal("0.50"),
                        new BigDecimal("0.20"));

        assertEquals(
                new BigDecimal("0.20"),
                context.effectiveMaximumCost());
    }

    @Test
    void workflowBudgetCanBeProvidedWithoutChangingRouterAccounting() {
        RoutingCostContext context =
                new RoutingCostContext(
                        null,
                        new BigDecimal("0.20"));

        assertEquals(
                new BigDecimal("0.20"),
                context.effectiveMaximumCost());
    }
}
