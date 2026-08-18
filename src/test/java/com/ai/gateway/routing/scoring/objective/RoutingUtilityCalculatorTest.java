package com.ai.gateway.routing.scoring.objective;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutingUtilityCalculatorTest {

    private final RoutingUtilityCalculator calculator =
            new RoutingUtilityCalculator();

    @Test
    void calculatesWeightedUtilityForAllObjectives() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.8,
                                RoutingObjective.QUALITY, 0.6,
                                RoutingObjective.RELIABILITY, 1.0));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 0.2,
                                RoutingObjective.QUALITY, 0.3,
                                RoutingObjective.RELIABILITY, 0.5));

        RoutingUtilityResult result =
                calculator.calculate(vector, weights);

        assertEquals(0.84, result.utility(), 1.0e-9);
        assertEquals(0.20, result.effectiveWeightOf(RoutingObjective.COST), 1.0e-9);
        assertEquals(0.18, result.contributionOf(RoutingObjective.QUALITY), 1.0e-9);
        assertEquals(0.50, result.contributionOf(RoutingObjective.RELIABILITY), 1.0e-9);
    }

    @Test
    void utilityIsBoundedAtOneForMaximumObjectives() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 1.0,
                                RoutingObjective.QUALITY, 1.0));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 2.0,
                                RoutingObjective.QUALITY, 3.0));

        assertEquals(
                1.0,
                calculator.calculate(vector, weights).utility(),
                1.0e-12);
    }

    @Test
    void utilityIsZeroForZeroObjectives() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.0,
                                RoutingObjective.QUALITY, 0.0));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 1.0,
                                RoutingObjective.QUALITY, 1.0));

        assertEquals(
                0.0,
                calculator.calculate(vector, weights).utility(),
                1.0e-12);
    }

    @Test
    void supportsZeroWeightObjectives() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.1,
                                RoutingObjective.QUALITY, 0.9));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 0.0,
                                RoutingObjective.QUALITY, 1.0));

        RoutingUtilityResult result =
                calculator.calculate(vector, weights);

        assertEquals(0.9, result.utility(), 1.0e-12);
        assertEquals(0.0, result.effectiveWeightOf(RoutingObjective.COST), 1.0e-12);
    }

    @Test
    void reNormalizesWeightsWhenAnObjectiveIsMissing() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(RoutingObjective.QUALITY, 0.8));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 0.75,
                                RoutingObjective.QUALITY, 0.25));

        RoutingUtilityResult result =
                calculator.calculate(vector, weights);

        assertEquals(0.8, result.utility(), 1.0e-12);
        assertEquals(1.0, result.effectiveWeightOf(RoutingObjective.QUALITY), 1.0e-12);
        assertEquals(1.0,
                result.effectiveWeights().values().stream()
                        .mapToDouble(Double::doubleValue)
                        .sum(),
                1.0e-12);
    }

    @Test
    void ignoresUnweightedVectorObjectives() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.2,
                                RoutingObjective.QUALITY, 0.9));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(RoutingObjective.QUALITY, 1.0));

        RoutingUtilityResult result =
                calculator.calculate(vector, weights);

        assertEquals(0.9, result.utility(), 1.0e-12);
        assertEquals(0.0, result.contributionOf(RoutingObjective.COST), 1.0e-12);
    }

    @Test
    void rejectsWhenNoWeightedObjectiveIsAvailable() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(RoutingObjective.COST, 0.5));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(RoutingObjective.QUALITY, 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(vector, weights));
    }

    @Test
    void rejectsNullVector() {
        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(RoutingObjective.COST, 1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(null, weights));
    }

    @Test
    void rejectsNullWeights() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(RoutingObjective.COST, 0.5));

        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.calculate(vector, null));
    }

    @Test
    void exposesPerObjectiveContributions() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.4,
                                RoutingObjective.QUALITY, 0.8));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 1.0,
                                RoutingObjective.QUALITY, 3.0));

        RoutingUtilityResult result =
                calculator.calculate(vector, weights);

        assertEquals(0.1, result.contributionOf(RoutingObjective.COST), 1.0e-12);
        assertEquals(0.6, result.contributionOf(RoutingObjective.QUALITY), 1.0e-12);
        assertEquals(0.7, result.utility(), 1.0e-12);
    }

    @Test
    void producesDeterministicResultsForSameInputs() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.7,
                                RoutingObjective.LATENCY, 0.4,
                                RoutingObjective.QUALITY, 0.9));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 2.0,
                                RoutingObjective.LATENCY, 1.0,
                                RoutingObjective.QUALITY, 3.0));

        RoutingUtilityResult first =
                calculator.calculate(vector, weights);
        RoutingUtilityResult second =
                calculator.calculate(vector, weights);

        assertEquals(first.utility(), second.utility(), 0.0);
        assertEquals(first.contributions(), second.contributions());
        assertEquals(first.effectiveWeights(), second.effectiveWeights());
    }

    @Test
    void preservesUtilityRangeForIntermediateValues() {
        RoutingObjectiveVector vector =
                new RoutingObjectiveVector(
                        Map.of(
                                RoutingObjective.COST, 0.25,
                                RoutingObjective.QUALITY, 0.75,
                                RoutingObjective.RELIABILITY, 0.5));

        RoutingObjectiveWeights weights =
                new RoutingObjectiveWeights(
                        Map.of(
                                RoutingObjective.COST, 0.25,
                                RoutingObjective.QUALITY, 0.50,
                                RoutingObjective.RELIABILITY, 0.25));

        double utility = calculator.calculate(vector, weights).utility();

        assertEquals(0.5625, utility, 1.0e-12);
        org.junit.jupiter.api.Assertions.assertTrue(utility >= 0.0 && utility <= 1.0);
    }
}
