package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveRoutingScoringServiceTest {
    @Test
    void latencyPriorityRaisesLatencyWeight() {
        RoutingScoringProperties properties = new RoutingScoringProperties();
        AdaptiveRoutingScoringService service = new AdaptiveRoutingScoringService(properties);
        RoutingDecisionContext context = new RoutingDecisionContext(
                UUID.randomUUID(), "T", null, null, java.util.Set.of(), false, false, null, RoutingPriority.LATENCY);

        var weights = service.adapt(context);

        assertTrue(weights.get(CandidateScoreDimension.LATENCY) > properties.getWeights().getLatency());
        assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    @Test
    void unityBoostsReliabilityAndPolicyWithoutChangingDeterministicContract() {
        RoutingScoringProperties properties = new RoutingScoringProperties();
        AdaptiveRoutingScoringService service = new AdaptiveRoutingScoringService(properties);
        RoutingDecisionContext context = new RoutingDecisionContext(
                UUID.randomUUID(), "T", null, null, java.util.Set.of("REASONING"), true, true,
                "research", RoutingPriority.BALANCED);

        var weights = service.adapt(context);

        assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
        assertTrue(weights.get(CandidateScoreDimension.AVAILABILITY) > 0.20);
        assertTrue(weights.get(CandidateScoreDimension.POLICY_PREFERENCE) > 0.25);
    }
}
