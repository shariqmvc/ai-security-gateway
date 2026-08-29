package com.ai.gateway.core.cost.routing;

import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.service.PreRequestCostEstimator;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.model.Provider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CostAwareRoutingEvaluatorTest {

    @Test
    void filtersCandidateThatExceedsRequestCostGuardrail() {
        PreRequestCostEstimator estimator =
                mock(PreRequestCostEstimator.class);

        when(estimator.estimate(any()))
                .thenAnswer(invocation -> {
                    var request = invocation.<com.ai.gateway.core.cost.dto.PreRequestCostRequest>getArgument(0);
                    BigDecimal cost =
                            request.getProvider() == Provider.OPENAI
                                    ? new BigDecimal("0.10")
                                    : new BigDecimal("0.40");

                    return PreRequestCostEstimate.builder()
                            .provider(request.getProvider())
                            .model(request.getModel())
                            .inputCost(cost)
                            .outputCost(BigDecimal.ZERO)
                            .cachedInputCost(BigDecimal.ZERO)
                            .additionalEstimatedCost(BigDecimal.ZERO)
                            .totalEstimatedCost(cost)
                            .build();
                });

        CostAwareRoutingEvaluator evaluator =
                new CostAwareRoutingEvaluator(
                        estimator,
                        new DefaultCostGuardrail());

        List<CostAwareCandidate> result =
                evaluator.evaluate(
                        List.of(
                                new RoutingCandidate(Provider.OPENAI, "gpt-test"),
                                new RoutingCandidate(Provider.GEMINI, "gemini-test")),
                        100,
                        100,
                        RoutingCostContext.requestOnly(
                                new BigDecimal("0.20")));

        assertEquals(1, result.size());
        assertEquals(Provider.OPENAI, result.get(0).candidate().provider());
    }

    @Test
    void usesEffectiveWorkflowBudgetAsAnAdditionalHardBoundary() {
        PreRequestCostEstimator estimator =
                mock(PreRequestCostEstimator.class);

        when(estimator.estimate(any()))
                .thenReturn(PreRequestCostEstimate.builder()
                        .provider(Provider.OPENAI)
                        .model("gpt-test")
                        .inputCost(new BigDecimal("0.15"))
                        .outputCost(BigDecimal.ZERO)
                        .cachedInputCost(BigDecimal.ZERO)
                        .additionalEstimatedCost(BigDecimal.ZERO)
                        .totalEstimatedCost(new BigDecimal("0.15"))
                        .build());

        CostAwareRoutingEvaluator evaluator =
                new CostAwareRoutingEvaluator(
                        estimator,
                        new DefaultCostGuardrail());

        List<CostAwareCandidate> result =
                evaluator.evaluate(
                        List.of(new RoutingCandidate(
                                Provider.OPENAI, "gpt-test")),
                        100,
                        100,
                        new RoutingCostContext(
                                new BigDecimal("0.50"),
                                new BigDecimal("0.10")));

        assertTrue(result.isEmpty());
    }
}
