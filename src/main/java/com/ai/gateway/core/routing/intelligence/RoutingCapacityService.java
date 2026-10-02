package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;

public interface RoutingCapacityService {
    RoutingCapacitySignals snapshot();
    boolean tryAcquire(RoutingCandidate candidate);
    void release(RoutingCandidate candidate);
    void recordCompletedRequest(RoutingCandidate candidate, long totalTokens);
    int inFlight(RoutingCandidate candidate);
    double utilization(RoutingCandidate candidate);
    boolean isAtHardLimit(RoutingCandidate candidate);
}
