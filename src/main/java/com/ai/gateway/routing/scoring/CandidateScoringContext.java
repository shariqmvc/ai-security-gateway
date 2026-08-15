package com.ai.gateway.routing.scoring;

import com.ai.gateway.routing.intelligence.RoutingDecisionContext;
import com.ai.gateway.routing.intelligence.RoutingRuntimeSignals;
import com.ai.gateway.routing.policy.RoutingPolicy;

import java.util.Map;
import java.util.Objects;

public record CandidateScoringContext(
        RoutingPolicy policy,
        int estimatedInputTokens,
        int estimatedOutputTokens,
        boolean extensiveResearchEnabled,
        String executionRole,
        RoutingDecisionContext decisionContext,
        Map<CandidateScoreDimension, Double> weightOverrides,
        RoutingRuntimeSignals runtimeSignals) {

    public CandidateScoringContext {
        Objects.requireNonNull(policy, "Routing policy is required.");
        if (estimatedInputTokens < 0 || estimatedOutputTokens < 0) {
            throw new IllegalArgumentException("Estimated token counts cannot be negative.");
        }
        weightOverrides = weightOverrides == null ? Map.of() : Map.copyOf(weightOverrides);
        runtimeSignals = runtimeSignals == null ? RoutingRuntimeSignals.empty() : runtimeSignals;
    }

    public CandidateScoringContext(
            RoutingPolicy policy,
            int estimatedInputTokens,
            int estimatedOutputTokens,
            boolean extensiveResearchEnabled,
            String executionRole) {
        this(policy, estimatedInputTokens, estimatedOutputTokens,
                extensiveResearchEnabled, executionRole, null, Map.of(), RoutingRuntimeSignals.empty());
    }

    public static CandidateScoringContext standard(RoutingPolicy policy) {
        return new CandidateScoringContext(policy, 1_000, 1_000, false, null);
    }

    public CandidateScoringContext forUnityRole(String role) {
        return new CandidateScoringContext(
                policy, estimatedInputTokens, estimatedOutputTokens, true, role,
                decisionContext, weightOverrides, runtimeSignals);
    }
}
