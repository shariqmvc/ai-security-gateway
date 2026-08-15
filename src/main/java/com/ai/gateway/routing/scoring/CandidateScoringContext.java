package com.ai.gateway.routing.scoring;

import com.ai.gateway.routing.policy.RoutingPolicy;

import java.util.Objects;

/**
 * Context supplied to candidate scoring.
 *
 * <p>The optional Unity fields intentionally remain generic at this stage.
 * Unity can later provide node/workflow-specific scoring context without
 * changing the scoring strategy contract.</p>
 */
public record CandidateScoringContext(
        RoutingPolicy policy,
        int estimatedInputTokens,
        int estimatedOutputTokens,
        boolean extensiveResearchEnabled,
        String executionRole) {

    public CandidateScoringContext {
        Objects.requireNonNull(policy, "Routing policy is required.");
        if (estimatedInputTokens < 0) {
            throw new IllegalArgumentException(
                    "Estimated input tokens cannot be negative.");
        }
        if (estimatedOutputTokens < 0) {
            throw new IllegalArgumentException(
                    "Estimated output tokens cannot be negative.");
        }
    }

    public static CandidateScoringContext standard(RoutingPolicy policy) {
        return new CandidateScoringContext(
                policy,
                1_000,
                1_000,
                false,
                null);
    }

    public CandidateScoringContext forUnityRole(String role) {
        return new CandidateScoringContext(
                policy,
                estimatedInputTokens,
                estimatedOutputTokens,
                true,
                role);
    }
}
