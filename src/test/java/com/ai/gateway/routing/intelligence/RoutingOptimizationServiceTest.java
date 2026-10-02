package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class RoutingOptimizationServiceTest {

    @Test
    void degradesOnlyTheCandidateWithConfidentPoorAvailability() {
        Map<CandidateScoreDimension, Double> base = baseWeights();

        RoutingCandidate healthy =
                new RoutingCandidate(Provider.OPENAI, "gpt-5", "openai-primary");
        RoutingCandidate degraded =
                new RoutingCandidate(Provider.GEMINI, "gemini-2.5-pro", "gemini-primary");

        RoutingRuntimeSignals signals = new RoutingRuntimeSignals(
                Map.of(),
                Map.of(
                        healthy.candidateKey(), 0.98,
                        degraded.candidateKey(), 0.80),
                Map.of(
                        healthy.candidateKey(), 10L,
                        degraded.candidateKey(), 10L));

        RoutingOptimizationService service = new RoutingOptimizationService();

        RoutingOptimizationService.RoutingCandidateOptimization healthyResult =
                service.optimizeCandidate(healthy, signals);
        RoutingOptimizationService.RoutingCandidateOptimization degradedResult =
                service.optimizeCandidate(degraded, signals);

        assertEquals(1.0, healthyResult.scoreMultiplier(), 1e-9);
        assertNull(healthyResult.reason());
        assertEquals(0.85, degradedResult.scoreMultiplier(), 1e-9);
        assertEquals("DEGRADED_AVAILABILITY", degradedResult.reason());
    }

    @Test
    void appliesStrongerAdjustmentToConfidentUnhealthyCandidate() {
        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-5", "openai-primary");

        RoutingRuntimeSignals signals = new RoutingRuntimeSignals(
                Map.of(),
                Map.of(candidate.candidateKey(), 0.50),
                Map.of(candidate.candidateKey(), 10L));

        RoutingOptimizationService.RoutingCandidateOptimization result =
                new RoutingOptimizationService().optimizeCandidate(candidate, signals);

        assertEquals(0.60, result.scoreMultiplier(), 1e-9);
        assertEquals("UNHEALTHY_AVAILABILITY", result.reason());
    }

    @Test
    void doesNotPenalizeCandidateWithInsufficientObservations() {
        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-5", "openai-primary");

        RoutingRuntimeSignals signals = new RoutingRuntimeSignals(
                Map.of(),
                Map.of(candidate.candidateKey(), 0.20),
                Map.of(candidate.candidateKey(), 2L));

        RoutingOptimizationService.RoutingCandidateOptimization result =
                new RoutingOptimizationService().optimizeCandidate(candidate, signals);

        assertEquals(1.0, result.scoreMultiplier(), 1e-9);
        assertNull(result.reason());
    }

    @Test
    void preservesPriorityOptimizationWithoutGlobalHealthBoost() {
        Map<CandidateScoreDimension, Double> base = baseWeights();

        Map<CandidateScoreDimension, Double> result =
                new RoutingOptimizationService().optimize(
                        base,
                        new RoutingRuntimeSignals(
                                Map.of("OPENAI:gpt-5", 500.0),
                                Map.of("OPENAI:gpt-5", 0.20),
                                Map.of("OPENAI:gpt-5", 10L)),
                        RoutingPriority.LATENCY);

        assertTrue(result.get(CandidateScoreDimension.LATENCY)
                > base.get(CandidateScoreDimension.LATENCY));
        assertTrue(
                result.get(CandidateScoreDimension.AVAILABILITY)
                        < base.get(CandidateScoreDimension.AVAILABILITY),
                "Normalizing the boosted weights must reduce the relative share of unaffected dimensions.");
        assertEquals(
                1.0,
                result.values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
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

    private Map<CandidateScoreDimension, Double> baseWeights() {
        Map<CandidateScoreDimension, Double> base =
                new EnumMap<>(CandidateScoreDimension.class);
        base.put(CandidateScoreDimension.COST, 0.30);
        base.put(CandidateScoreDimension.LATENCY, 0.25);
        base.put(CandidateScoreDimension.AVAILABILITY, 0.20);
        base.put(CandidateScoreDimension.POLICY_PREFERENCE, 0.25);
        return base;
    }
}
