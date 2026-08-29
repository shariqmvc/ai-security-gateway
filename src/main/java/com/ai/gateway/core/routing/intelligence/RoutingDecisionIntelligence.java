package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;

import java.util.List;

public interface RoutingDecisionIntelligence {
    RoutingDecisionContext context(RoutingContext routingContext);
    List<RoutingCandidate> applyCapabilityMatching(List<RoutingCandidate> candidates, RoutingDecisionContext context);
    CandidateScoringContext scoringContext(RoutingPolicy policy, RoutingDecisionContext context);
    RoutingDecisionExplanation explain(RoutingDecisionContext context, int candidateCount, String reason);
}
