package com.ai.gateway.routing.scoring.objective;

import java.util.EnumMap;
import java.util.Map;

/** Immutable normalized objective vector. Every supplied value is in [0, 1]. */
public record RoutingObjectiveVector(Map<RoutingObjective, Double> values) {

    public RoutingObjectiveVector {
        EnumMap<RoutingObjective, Double> copy =
                new EnumMap<>(RoutingObjective.class);

        if (values != null) {
            values.forEach((objective, value) -> {
                if (objective == null) {
                    throw new IllegalArgumentException("Objective cannot be null.");
                }
                if (value == null || !Double.isFinite(value)
                        || value < 0.0 || value > 1.0) {
                    throw new IllegalArgumentException(
                            "Normalized objective value must be finite and between 0 and 1.");
                }
                copy.put(objective, value);
            });
        }

        values = Map.copyOf(copy);
    }

    public double valueOrDefault(
            RoutingObjective objective,
            double defaultValue) {
        return values.getOrDefault(objective, defaultValue);
    }

    public boolean contains(RoutingObjective objective) {
        return values.containsKey(objective);
    }
}
