package com.ai.gateway.cost.routing;

import java.math.BigDecimal;

public interface CostGuardrail {

    CostGuardrailDecision evaluate(
            BigDecimal estimatedCost,
            BigDecimal maximumRequestCost);
}
