package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.routing.policy.RoutingPolicy;

import java.util.List;

public interface CandidateEligibilityFilter {

    List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates,
            RoutingPolicy policy);
}
