package com.ai.gateway.core.routing.scoring.objective;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingObjectiveWeightsTest {

    @Test
    void normalizesConfiguredWeights() {
        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 2.0,
                                RoutingObjective.QUALITY, 1.0,
                                RoutingObjective.RELIABILITY, 1.0));

        assertEquals(0.50, weights.weightOf(RoutingObjective.COST), 1.0e-9);
        assertEquals(0.25, weights.weightOf(RoutingObjective.QUALITY), 1.0e-9);
        assertEquals(0.25, weights.weightOf(RoutingObjective.RELIABILITY), 1.0e-9);
        assertEquals(1.0,
                weights.values().values().stream().mapToDouble(Double::doubleValue).sum(),
                1.0e-9);
    }

    @Test
    void rejectsNegativeOrZeroTotalWeights() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoutingObjectiveWeights(
                        Map.of(RoutingObjective.COST, -1.0)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RoutingObjectiveWeights(
                        Map.of(RoutingObjective.COST, 0.0,
                                RoutingObjective.QUALITY, 0.0)));
    }
}
