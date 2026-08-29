package com.ai.gateway.core.cost.routing;

import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.routing.engine.RoutingCandidate;

import java.util.Objects;

public record CostAwareCandidate(
        RoutingCandidate candidate,
        PreRequestCostEstimate estimate) {

    public CostAwareCandidate {
        Objects.requireNonNull(candidate, "Candidate is required.");
        Objects.requireNonNull(estimate, "Cost estimate is required.");
    }
}
