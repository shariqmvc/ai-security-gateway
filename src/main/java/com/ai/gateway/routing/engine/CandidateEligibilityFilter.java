package com.ai.gateway.routing.engine;

import com.ai.gateway.routing.policy.RoutingPolicy;

import java.util.List;

public interface CandidateEligibilityFilter {

    List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates,
            RoutingPolicy policy);
}
