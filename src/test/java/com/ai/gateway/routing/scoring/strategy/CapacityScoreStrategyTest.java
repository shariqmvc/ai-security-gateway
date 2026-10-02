package com.ai.gateway.core.routing.scoring.strategy;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.intelligence.RoutingCapacitySignals;
import com.ai.gateway.core.routing.intelligence.RoutingRuntimeSignals;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapacityScoreStrategyTest {

    @Test
    void lowerUtilizationGetsHigherCapacityScore() {
        RoutingCandidate idle =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "idle");
        RoutingCandidate busy =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "busy");

        RoutingCapacitySignals capacity = new RoutingCapacitySignals(
                Map.of(idle.candidateKey(), 1, busy.candidateKey(), 4),
                Map.of(),
                Map.of(),
                Map.of(idle.candidateKey(), 0.25, busy.candidateKey(), 0.80));

        CandidateScoringContext context = new CandidateScoringContext(
                new RoutingPolicy(true, java.util.List.of(), java.util.List.of(), null, null),
                100,
                100,
                false,
                null,
                null,
                Map.of(),
                new RoutingRuntimeSignals(
                        Map.of(), Map.of(), Map.of(), capacity));

        CapacityScoreStrategy strategy = new CapacityScoreStrategy();

        assertEquals(0.75, strategy.rawScore(idle, context), 1.0e-9);
        assertEquals(0.20, strategy.rawScore(busy, context), 1.0e-9);
    }
}
