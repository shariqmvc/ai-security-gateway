package com.ai.gateway.routing.scoring.objective.profile;

import com.ai.gateway.routing.scoring.objective.RoutingObjectiveWeights;

import java.util.Objects;

/**
 * Immutable routing optimization profile.
 *
 * <p>A profile is a named, validated set of multi-objective weights.
 * Hard routing constraints are deliberately outside this model.</p>
 */
public record RoutingOptimizationProfile(
        String name,
        RoutingOptimizationProfileType type,
        RoutingObjectiveWeights weights) {

    public RoutingOptimizationProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Optimization profile name is required.");
        }
        name = name.trim();

        type = Objects.requireNonNull(
                type,
                "Optimization profile type is required.");

        weights = Objects.requireNonNull(
                weights,
                "Optimization profile weights are required.");
    }
}
