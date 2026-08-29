package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;

import java.util.List;

public interface CandidateCapabilityMatcher {
    List<RoutingCandidate> filter(List<RoutingCandidate> candidates, RoutingDecisionContext context);
}
