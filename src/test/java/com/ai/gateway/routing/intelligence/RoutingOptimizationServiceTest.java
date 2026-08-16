package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@ActiveProfiles("test")
class RoutingOptimizationServiceTest {

    @Test
    void boostsAvailabilityWhenRuntimeHealthDegrades() {
        Map<CandidateScoreDimension, Double> base = new EnumMap<>(CandidateScoreDimension.class);
        base.put(CandidateScoreDimension.COST, 0.30);
        base.put(CandidateScoreDimension.LATENCY, 0.25);
        base.put(CandidateScoreDimension.AVAILABILITY, 0.20);
        base.put(CandidateScoreDimension.POLICY_PREFERENCE, 0.25);

        RoutingRuntimeSignals signals = new RoutingRuntimeSignals(
                Map.of("OPENAI:gpt-5", 500.0),
                Map.of("OPENAI:gpt-5", 0.80));

        Map<CandidateScoreDimension, Double> result =
                new RoutingOptimizationService().optimize(
                        base, signals, RoutingPriority.BALANCED);

        assertTrue(result.get(CandidateScoreDimension.AVAILABILITY) > 0.20);
        assertEquals(1.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void preservesGovernanceByOnlyChangingSoftWeights() {
        Map<CandidateScoreDimension, Double> base = Map.of(
                CandidateScoreDimension.COST, 0.5,
                CandidateScoreDimension.POLICY_PREFERENCE, 0.5);

        Map<CandidateScoreDimension, Double> result =
                new RoutingOptimizationService().optimize(
                        base, RoutingRuntimeSignals.empty(), RoutingPriority.COST);

        assertTrue(result.get(CandidateScoreDimension.COST) > 0.5);
        assertEquals(1.0, result.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }
}
