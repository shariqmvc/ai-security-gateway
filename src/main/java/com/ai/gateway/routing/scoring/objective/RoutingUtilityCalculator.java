package com.ai.gateway.routing.scoring.objective;

import java.util.EnumMap;
import java.util.Map;

/**
 * Calculates a deterministic weighted utility from normalized routing
 * objectives.
 *
 * <p>Only objectives present in both the vector and the configured weights
 * participate. When an objective is absent from the vector, the remaining
 * active weights are re-normalized so a sparse vector does not receive an
 * artificial penalty merely because an unavailable metric was not supplied.</p>
 */
public final class RoutingUtilityCalculator {

    private static final double EPSILON = 1.0e-12;

    public RoutingUtilityResult calculate(
            RoutingObjectiveVector vector,
            RoutingObjectiveWeights weights) {

        if (vector == null) {
            throw new IllegalArgumentException("Routing objective vector is required.");
        }
        if (weights == null) {
            throw new IllegalArgumentException("Routing objective weights are required.");
        }

        EnumMap<RoutingObjective, Double> activeWeights =
                new EnumMap<>(RoutingObjective.class);

        double activeWeightTotal = 0.0;
        for (Map.Entry<RoutingObjective, Double> entry : weights.values().entrySet()) {
            if (vector.contains(entry.getKey()) && entry.getValue() > 0.0) {
                activeWeights.put(entry.getKey(), entry.getValue());
                activeWeightTotal += entry.getValue();
            }
        }

        if (activeWeightTotal <= EPSILON) {
            throw new IllegalArgumentException(
                    "At least one weighted objective must be present in the objective vector.");
        }

        double finalActiveWeightTotal = activeWeightTotal;
        activeWeights.replaceAll(
                (objective, weight) -> weight / finalActiveWeightTotal);

        EnumMap<RoutingObjective, Double> contributions =
                new EnumMap<>(RoutingObjective.class);

        double utility = 0.0;
        for (Map.Entry<RoutingObjective, Double> entry : activeWeights.entrySet()) {
            double normalizedValue = vector.valueOrDefault(entry.getKey(), 0.0);
            double contribution = normalizedValue * entry.getValue();

            if (!Double.isFinite(contribution)) {
                throw new IllegalStateException(
                        "Routing objective contribution must be finite.");
            }

            contributions.put(entry.getKey(), contribution);
            utility += contribution;
        }

        // Protect the contract against floating-point accumulation drift.
        utility = clamp(utility);

        return new RoutingUtilityResult(
                utility,
                contributions,
                activeWeights);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
