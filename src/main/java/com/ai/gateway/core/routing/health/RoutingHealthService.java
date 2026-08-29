package com.ai.gateway.core.routing.health;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;

import java.util.List;

public interface RoutingHealthService {
    RoutingHealthSnapshot snapshot(RoutingCandidate candidate);
    List<RoutingHealthSnapshot> snapshots();
    void recordSuccess(RoutingCandidate candidate, long latencyMs);
    void recordFailure(RoutingCandidate candidate, String failureCategory);
    boolean isHealthyForRouting(RoutingCandidate candidate);
}
