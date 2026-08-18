package com.ai.gateway.cost.routing;

import com.ai.gateway.cost.dto.PreRequestCostRequest;
import com.ai.gateway.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.cost.service.PreRequestCostEstimator;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CostAwareRoutingEvaluator {

    private final PreRequestCostEstimator estimator;
    private final CostGuardrail guardrail;

    public List<CostAwareCandidate> evaluate(
            List<RoutingCandidate> candidates,
            int estimatedInputTokens,
            int estimatedOutputTokens,
            RoutingCostContext costContext) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (estimatedInputTokens < 0 || estimatedOutputTokens < 0) {
            throw new IllegalArgumentException(
                    "Estimated token counts cannot be negative.");
        }

        RoutingCostContext context =
                costContext == null
                        ? RoutingCostContext.requestOnly(null)
                        : costContext;

        List<CostAwareCandidate> eligible =
                new ArrayList<>(candidates.size());

        for (RoutingCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }

            PreRequestCostRequest request =
                    PreRequestCostRequest.builder()
                            .provider(candidate.provider())
                            .model(candidate.model())
                            .inputTokens(estimatedInputTokens)
                            .outputTokens(estimatedOutputTokens)
                            .build();

            PreRequestCostEstimate estimate =
                    estimator.estimate(request);

            CostGuardrailDecision decision =
                    guardrail.evaluate(
                            estimate.getTotalEstimatedCost(),
                            context.effectiveMaximumCost());

            if (decision.allowed()) {
                eligible.add(
                        new CostAwareCandidate(candidate, estimate));
            }
        }

        return List.copyOf(eligible);
    }
}
