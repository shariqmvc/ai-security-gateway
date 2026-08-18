package com.ai.gateway.routing.scoring.objective;

import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable result of multi-objective utility calculation.
 *
 * @param utility final weighted utility in [0, 1]
 * @param contributions per-objective weighted contributions
 * @param effectiveWeights normalized weights for objectives that were actually
 *                         available in the supplied vector
 */
public record RoutingUtilityResult(
        double utility,
        Map<RoutingObjective, Double> contributions,
        Map<RoutingObjective, Double> effectiveWeights) {

    public RoutingUtilityResult {
        if (!Double.isFinite(utility) || utility < 0.0 || utility > 1.0) {
            throw new IllegalArgumentException(
                    "Routing utility must be finite and between 0 and 1.");
        }

        contributions = immutableCopy(contributions, "contribution");
        effectiveWeights = immutableCopy(effectiveWeights, "weight");

        double weightTotal = effectiveWeights.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (Math.abs(weightTotal - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException(
                    "Effective routing utility weights must sum to 1.");
        }

        for (Map.Entry<RoutingObjective, Double> entry : contributions.entrySet()) {
            if (entry.getValue() < 0.0 || entry.getValue() > 1.0
                    || !Double.isFinite(entry.getValue())) {
                throw new IllegalArgumentException(
                        "Routing utility contribution must be finite and between 0 and 1.");
            }
        }
    }

    public double contributionOf(RoutingObjective objective) {
        return contributions.getOrDefault(objective, 0.0);
    }

    public double effectiveWeightOf(RoutingObjective objective) {
        return effectiveWeights.getOrDefault(objective, 0.0);
    }

    private static Map<RoutingObjective, Double> immutableCopy(
            Map<RoutingObjective, Double> source,
            String label) {

        EnumMap<RoutingObjective, Double> copy =
                new EnumMap<>(RoutingObjective.class);

        if (source != null) {
            source.forEach((objective, value) -> {
                if (objective == null) {
                    throw new IllegalArgumentException(
                            "Routing objective " + label + " cannot be null.");
                }
                if (value == null || !Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException(
                            "Routing objective " + label
                                    + " must be finite and non-negative.");
                }
                copy.put(objective, value);
            });
        }

        return Map.copyOf(copy);
    }
}
