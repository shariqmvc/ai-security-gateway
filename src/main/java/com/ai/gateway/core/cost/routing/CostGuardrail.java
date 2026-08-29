package com.ai.gateway.core.cost.routing;

import java.math.BigDecimal;

public interface CostGuardrail {

    CostGuardrailDecision evaluate(
            BigDecimal estimatedCost,
            BigDecimal maximumRequestCost);
}
