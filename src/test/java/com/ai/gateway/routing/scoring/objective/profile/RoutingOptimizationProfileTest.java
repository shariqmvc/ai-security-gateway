package com.ai.gateway.core.routing.scoring.objective.profile;

import com.ai.gateway.core.routing.scoring.objective.RoutingObjective;
import com.ai.gateway.core.routing.scoring.objective.RoutingObjectiveWeights;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutingOptimizationProfileTest {

    @Test
    void registersAllBuiltInProfiles() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        assertEquals(4, registry.size());
        assertNotNull(registry.get("BALANCED"));
        assertNotNull(registry.get("cost_optimized"));
        assertNotNull(registry.get("QUALITY_OPTIMIZED"));
        assertNotNull(registry.get("latency_optimized"));
    }

    @Test
    void builtInWeightsMatchDeclaredProfiles() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfile balanced =
                registry.get(RoutingOptimizationProfileRegistry.BALANCED);

        assertEquals(
                0.15,
                balanced.weights().weightOf(RoutingObjective.COST),
                1.0e-9);
        assertEquals(
                0.20,
                balanced.weights().weightOf(RoutingObjective.QUALITY),
                1.0e-9);
        assertEquals(
                1.0,
                balanced.weights().values().values()
                        .stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1.0e-9);

        RoutingOptimizationProfile cost =
                registry.get(RoutingOptimizationProfileRegistry.COST_OPTIMIZED);

        assertTrue(
                cost.weights().weightOf(RoutingObjective.COST)
                        > balanced.weights().weightOf(RoutingObjective.COST));

        RoutingOptimizationProfile quality =
                registry.get(RoutingOptimizationProfileRegistry.QUALITY_OPTIMIZED);

        assertEquals(
                0.40,
                quality.weights().weightOf(RoutingObjective.QUALITY),
                1.0e-9);

        RoutingOptimizationProfile latency =
                registry.get(RoutingOptimizationProfileRegistry.LATENCY_OPTIMIZED);

        assertEquals(
                0.40,
                latency.weights().weightOf(RoutingObjective.LATENCY),
                1.0e-9);
    }

    @Test
    void customProfilesCanBeRegistered() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfile custom =
                new RoutingOptimizationProfile(
                        "enterprise_quality",
                        RoutingOptimizationProfileType.CUSTOM,
                        new RoutingObjectiveWeights(
                                Map.of(
                                        RoutingObjective.QUALITY, 4.0,
                                        RoutingObjective.RELIABILITY, 1.0)));

        registry.register(custom);

        RoutingOptimizationProfile resolved =
                registry.get("ENTERPRISE_QUALITY");

        assertNotNull(resolved);
        assertEquals(
                RoutingOptimizationProfileType.CUSTOM,
                resolved.type());
        assertEquals(
                0.80,
                resolved.weights().weightOf(RoutingObjective.QUALITY),
                1.0e-9);
        assertEquals(
                0.20,
                resolved.weights().weightOf(RoutingObjective.RELIABILITY),
                1.0e-9);
    }

    @Test
    void rejectsInvalidCustomProfile() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RoutingOptimizationProfile(
                        " ",
                        RoutingOptimizationProfileType.CUSTOM,
                        new RoutingObjectiveWeights(
                                Map.of(RoutingObjective.QUALITY, 1.0))));

        assertThrows(
                NullPointerException.class,
                () -> new RoutingOptimizationProfile(
                        "CUSTOM",
                        RoutingOptimizationProfileType.CUSTOM,
                        null));
    }

    @Test
    void rejectsChangingBuiltInProfileType() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfile invalid =
                new RoutingOptimizationProfile(
                        "BALANCED",
                        RoutingOptimizationProfileType.CUSTOM,
                        new RoutingObjectiveWeights(
                                Map.of(RoutingObjective.QUALITY, 1.0)));

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(invalid));
    }
}
