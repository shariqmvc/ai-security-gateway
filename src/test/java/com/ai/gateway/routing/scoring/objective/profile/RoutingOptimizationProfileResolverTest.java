package com.ai.gateway.routing.scoring.objective.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingOptimizationProfileResolverTest {

    @Test
    void explicitProfileHasHighestPrecedence() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfileResolver resolver =
                new RoutingOptimizationProfileResolver(
                        registry,
                        "QUALITY_OPTIMIZED");

        RoutingOptimizationProfile result =
                resolver.resolve(
                        "LATENCY_OPTIMIZED",
                        "COST_OPTIMIZED");

        assertEquals(
                RoutingOptimizationProfileType.LATENCY_OPTIMIZED,
                result.type());
    }

    @Test
    void tenantPolicyProfileOverridesConfiguredDefault() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfileResolver resolver =
                new RoutingOptimizationProfileResolver(
                        registry,
                        "QUALITY_OPTIMIZED");

        RoutingOptimizationProfile result =
                resolver.resolve(
                        null,
                        "COST_OPTIMIZED");

        assertEquals(
                RoutingOptimizationProfileType.COST_OPTIMIZED,
                result.type());
    }

    @Test
    void configuredDefaultIsUsedWhenNoHigherPrecedenceProfileExists() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfileResolver resolver =
                new RoutingOptimizationProfileResolver(
                        registry,
                        "QUALITY_OPTIMIZED");

        RoutingOptimizationProfile result =
                resolver.resolve(null, null);

        assertEquals(
                RoutingOptimizationProfileType.QUALITY_OPTIMIZED,
                result.type());
    }

    @Test
    void balancedIsFallbackWhenDefaultIsBlank() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfileResolver resolver =
                new RoutingOptimizationProfileResolver(
                        registry,
                        " ");

        assertEquals(
                RoutingOptimizationProfileType.BALANCED,
                resolver.resolve().type());
    }

    @Test
    void unknownExplicitOrTenantProfileFallsThrough() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        RoutingOptimizationProfileResolver resolver =
                new RoutingOptimizationProfileResolver(
                        registry,
                        "BALANCED");

        assertEquals(
                RoutingOptimizationProfileType.BALANCED,
                resolver.resolve(
                        "DOES_NOT_EXIST",
                        "ALSO_UNKNOWN").type());
    }

    @Test
    void rejectsUnknownConfiguredDefault() {
        RoutingOptimizationProfileRegistry registry =
                new RoutingOptimizationProfileRegistry();

        assertThrows(
                IllegalArgumentException.class,
                () -> new RoutingOptimizationProfileResolver(
                        registry,
                        "DOES_NOT_EXIST"));
    }
}
