package com.ai.gateway.cost.routing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCostGuardrailTest {

    private final DefaultCostGuardrail guardrail =
            new DefaultCostGuardrail();

    @Test
    void allowsEstimateAtLimit() {
        CostGuardrailDecision result =
                guardrail.evaluate(
                        new BigDecimal("0.100"),
                        new BigDecimal("0.100"));

        assertTrue(result.allowed());
        assertEquals("WITHIN_REQUEST_COST_LIMIT", result.reason());
    }

    @Test
    void rejectsEstimateAboveLimit() {
        CostGuardrailDecision result =
                guardrail.evaluate(
                        new BigDecimal("0.101"),
                        new BigDecimal("0.100"));

        assertFalse(result.allowed());
        assertEquals("REQUEST_COST_LIMIT_EXCEEDED", result.reason());
    }

    @Test
    void allowsWhenNoLimitConfigured() {
        CostGuardrailDecision result =
                guardrail.evaluate(
                        new BigDecimal("0.101"),
                        null);

        assertTrue(result.allowed());
        assertEquals("NO_REQUEST_COST_LIMIT", result.reason());
    }
}
