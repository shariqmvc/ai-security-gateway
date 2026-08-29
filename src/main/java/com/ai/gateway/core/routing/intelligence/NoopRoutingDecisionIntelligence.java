package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;

import java.util.List;
import java.util.Set;

/** Compatibility implementation for isolated pre-6.6 routing tests. */
public final class NoopRoutingDecisionIntelligence implements RoutingDecisionIntelligence {
    @Override public RoutingDecisionContext context(RoutingContext context) {
        return new RoutingDecisionContext(null, null, null, null, Set.of(), false, false, null, RoutingPriority.BALANCED);
    }
    @Override public List<RoutingCandidate> applyCapabilityMatching(List<RoutingCandidate> candidates, RoutingDecisionContext context) {
        return candidates == null ? List.of() : candidates;
    }
    @Override public CandidateScoringContext scoringContext(RoutingPolicy policy, RoutingDecisionContext context) {
        return CandidateScoringContext.standard(policy);
    }
    @Override public RoutingDecisionExplanation explain(RoutingDecisionContext context, int candidateCount, String reason) {
        return new RoutingDecisionExplanation(reason, List.of(), List.of(), List.of());
    }
}
