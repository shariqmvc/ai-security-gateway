package com.ai.gateway.routing.scoring.objective;

import java.util.EnumMap;
import java.util.Map;

/** Immutable normalized weights for the routing objective model. */
public record RoutingObjectiveWeights(Map<RoutingObjective, Double> values) {

    public RoutingObjectiveWeights {
        EnumMap<RoutingObjective, Double> copy =
                new EnumMap<>(RoutingObjective.class);

        if (values != null) {
            values.forEach((objective, weight) -> {
                if (objective == null) {
                    throw new IllegalArgumentException("Objective cannot be null.");
                }
                if (weight == null || !Double.isFinite(weight) || weight < 0.0) {
                    throw new IllegalArgumentException(
                            "Objective weights must be finite and non-negative.");
                }
                copy.put(objective, weight);
            });
        }

        double total = copy.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0.0) {
            throw new IllegalArgumentException(
                    "At least one routing objective weight must be greater than zero.");
        }

        copy.replaceAll((objective, weight) -> weight / total);
        values = Map.copyOf(copy);
    }

    public double weightOf(RoutingObjective objective) {
        return values.getOrDefault(objective, 0.0);
    }
}
