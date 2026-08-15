package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.engine.RoutingCandidate;

import java.util.List;

public interface CandidateCapabilityMatcher {
    List<RoutingCandidate> filter(List<RoutingCandidate> candidates, RoutingDecisionContext context);
}
