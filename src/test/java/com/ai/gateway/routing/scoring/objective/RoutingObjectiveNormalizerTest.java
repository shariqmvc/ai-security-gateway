package com.ai.gateway.core.routing.scoring.objective;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingObjectiveNormalizerTest {

    @Test
    void normalizesHigherIsBetter() {
        assertEquals(
                0.75,
                RoutingObjectiveNormalizer.normalize(
                        75.0,
                        new RoutingObjectiveBounds(0.0, 100.0),
                        RoutingObjectiveDirection.HIGHER_IS_BETTER),
                1.0e-9);
    }

    @Test
    void normalizesLowerIsBetter() {
        assertEquals(
                0.75,
                RoutingObjectiveNormalizer.normalize(
                        25.0,
                        new RoutingObjectiveBounds(0.0, 100.0),
                        RoutingObjectiveDirection.LOWER_IS_BETTER),
                1.0e-9);
    }

    @Test
    void clampsValuesOutsideConfiguredBounds() {
        assertEquals(
                1.0,
                RoutingObjectiveNormalizer.normalize(
                        150.0,
                        new RoutingObjectiveBounds(0.0, 100.0),
                        RoutingObjectiveDirection.HIGHER_IS_BETTER));

        assertEquals(
                0.0,
                RoutingObjectiveNormalizer.normalize(
                        150.0,
                        new RoutingObjectiveBounds(0.0, 100.0),
                        RoutingObjectiveDirection.LOWER_IS_BETTER));
    }

    @Test
    void constantMetricIsNeutralAndDeterministic() {
        assertEquals(
                1.0,
                RoutingObjectiveNormalizer.normalize(
                        25.0,
                        new RoutingObjectiveBounds(25.0, 25.0),
                        RoutingObjectiveDirection.HIGHER_IS_BETTER));
    }

    @Test
    void supportsExplicitMissingValuePolicies() {
        RoutingObjectiveBounds bounds =
                new RoutingObjectiveBounds(0.0, 100.0);

        assertEquals(
                0.5,
                RoutingObjectiveNormalizer.normalizeNullable(
                        null,
                        bounds,
                        RoutingObjectiveDirection.HIGHER_IS_BETTER,
                        RoutingObjectiveMissingValuePolicy.MIDPOINT));

        assertEquals(
                0.0,
                RoutingObjectiveNormalizer.normalizeNullable(
                        null,
                        bounds,
                        RoutingObjectiveDirection.HIGHER_IS_BETTER,
                        RoutingObjectiveMissingValuePolicy.ZERO));

        assertThrows(
                IllegalArgumentException.class,
                () -> RoutingObjectiveNormalizer.normalizeNullable(
                        null,
                        bounds,
                        RoutingObjectiveDirection.HIGHER_IS_BETTER,
                        RoutingObjectiveMissingValuePolicy.REJECT));
    }

    @Test
    void buildsSparseNormalizedObjectiveVector() {
        Map<RoutingObjective, Double> raw =
                Map.of(
                        RoutingObjective.COST, 20.0,
                        RoutingObjective.QUALITY, 90.0);

        Map<RoutingObjective, RoutingObjectiveBounds> bounds =
                new EnumMap<>(RoutingObjective.class);
        bounds.put(RoutingObjective.COST,
                new RoutingObjectiveBounds(0.0, 100.0));
        bounds.put(RoutingObjective.QUALITY,
                new RoutingObjectiveBounds(0.0, 100.0));

        Map<RoutingObjective, RoutingObjectiveDirection> directions =
                new EnumMap<>(RoutingObjective.class);
        directions.put(RoutingObjective.COST,
                RoutingObjectiveDirection.LOWER_IS_BETTER);
        directions.put(RoutingObjective.QUALITY,
                RoutingObjectiveDirection.HIGHER_IS_BETTER);

        RoutingObjectiveVector vector =
                RoutingObjectiveNormalizer.normalize(
                        raw,
                        bounds,
                        directions,
                        RoutingObjectiveMissingValuePolicy.MIDPOINT);

        assertEquals(0.80, vector.valueOrDefault(RoutingObjective.COST, 0.0), 1.0e-9);
        assertEquals(0.90, vector.valueOrDefault(RoutingObjective.QUALITY, 0.0), 1.0e-9);
        assertEquals(0.0, vector.valueOrDefault(RoutingObjective.LATENCY, 0.0));
    }

    @Test
    void rejectsInvalidBoundsAndDirections() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoutingObjectiveBounds(10.0, 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> RoutingObjectiveNormalizer.normalize(
                        Double.NaN,
                        new RoutingObjectiveBounds(0.0, 1.0),
                        RoutingObjectiveDirection.HIGHER_IS_BETTER));

        assertThrows(
                IllegalArgumentException.class,
                () -> RoutingObjectiveNormalizer.normalize(
                        0.5,
                        null,
                        RoutingObjectiveDirection.HIGHER_IS_BETTER));
    }
}
