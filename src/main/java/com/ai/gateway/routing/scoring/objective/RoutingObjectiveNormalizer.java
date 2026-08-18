package com.ai.gateway.routing.scoring.objective;

import java.util.EnumMap;
import java.util.Map;

/**
 * Normalizes routing metrics to the canonical [0, 1] scale.
 *
 * <p>Higher normalized values always mean "better". The normalizer never
 * silently converts a missing metric unless an explicit missing-value policy
 * is supplied.</p>
 */
public final class RoutingObjectiveNormalizer {

    private static final double MIDPOINT = 0.5;

    private RoutingObjectiveNormalizer() {
    }

    public static double normalize(
            double rawValue,
            RoutingObjectiveBounds bounds,
            RoutingObjectiveDirection direction) {

        if (!Double.isFinite(rawValue)) {
            throw new IllegalArgumentException("Objective raw value must be finite.");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("Objective bounds are required.");
        }
        if (direction == null) {
            throw new IllegalArgumentException("Objective direction is required.");
        }

        if (bounds.isConstant()) {
            return 1.0;
        }

        double normalized = switch (direction) {
            case HIGHER_IS_BETTER ->
                    (rawValue - bounds.minimum())
                            / (bounds.maximum() - bounds.minimum());
            case LOWER_IS_BETTER ->
                    (bounds.maximum() - rawValue)
                            / (bounds.maximum() - bounds.minimum());
        };

        return clamp(normalized);
    }

    public static Double normalizeNullable(
            Double rawValue,
            RoutingObjectiveBounds bounds,
            RoutingObjectiveDirection direction,
            RoutingObjectiveMissingValuePolicy missingValuePolicy) {

        if (rawValue != null) {
            return normalize(rawValue, bounds, direction);
        }

        if (missingValuePolicy == null) {
            throw new IllegalArgumentException(
                    "Missing-value policy is required when objective value is absent.");
        }

        return switch (missingValuePolicy) {
            case MIDPOINT -> MIDPOINT;
            case ZERO -> 0.0;
            case REJECT -> throw new IllegalArgumentException(
                    "Required routing objective value is unavailable.");
        };
    }

    /**
     * Normalizes a sparse objective map. Bounds and directions are explicit so
     * callers cannot accidentally normalize a cost metric as if higher were
     * better (or vice versa).
     */
    public static RoutingObjectiveVector normalize(
            Map<RoutingObjective, Double> rawValues,
            Map<RoutingObjective, RoutingObjectiveBounds> bounds,
            Map<RoutingObjective, RoutingObjectiveDirection> directions,
            RoutingObjectiveMissingValuePolicy missingValuePolicy) {

        EnumMap<RoutingObjective, Double> normalized =
                new EnumMap<>(RoutingObjective.class);

        Map<RoutingObjective, Double> raw =
                rawValues == null ? Map.of() : rawValues;
        Map<RoutingObjective, RoutingObjectiveBounds> configuredBounds =
                bounds == null ? Map.of() : bounds;
        Map<RoutingObjective, RoutingObjectiveDirection> configuredDirections =
                directions == null ? Map.of() : directions;

        for (RoutingObjective objective : RoutingObjective.values()) {
            Double rawValue = raw.get(objective);

            if (rawValue == null && !configuredBounds.containsKey(objective)) {
                if (missingValuePolicy == RoutingObjectiveMissingValuePolicy.REJECT) {
                    throw new IllegalArgumentException(
                            "Missing bounds for unavailable objective: " + objective);
                }
                continue;
            }

            RoutingObjectiveBounds objectiveBounds = configuredBounds.get(objective);
            RoutingObjectiveDirection direction = configuredDirections.get(objective);

            if (rawValue == null) {
                normalized.put(
                        objective,
                        normalizeNullable(
                                null,
                                objectiveBounds,
                                direction,
                                missingValuePolicy));
            } else {
                normalized.put(
                        objective,
                        normalize(rawValue, objectiveBounds, direction));
            }
        }

        return new RoutingObjectiveVector(normalized);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
