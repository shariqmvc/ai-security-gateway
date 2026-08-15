package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.scoring.CandidateScoringContext;

import java.util.List;

public interface RoutingDecisionIntelligence {
    RoutingDecisionContext context(RoutingContext routingContext);
    List<RoutingCandidate> applyCapabilityMatching(List<RoutingCandidate> candidates, RoutingDecisionContext context);
    CandidateScoringContext scoringContext(RoutingPolicy policy, RoutingDecisionContext context);
    RoutingDecisionExplanation explain(RoutingDecisionContext context, int candidateCount, String reason);
}
