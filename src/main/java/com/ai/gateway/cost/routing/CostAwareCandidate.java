package com.ai.gateway.cost.routing;

import com.ai.gateway.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.routing.engine.RoutingCandidate;

import java.util.Objects;

public record CostAwareCandidate(
        RoutingCandidate candidate,
        PreRequestCostEstimate estimate) {

    public CostAwareCandidate {
        Objects.requireNonNull(candidate, "Candidate is required.");
        Objects.requireNonNull(estimate, "Cost estimate is required.");
    }
}
