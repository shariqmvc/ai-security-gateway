package com.ai.gateway.routing.health;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.health.entity.RoutingOutcome;

public interface RoutingOutcomeService {
    void recordSuccess(java.util.UUID requestId, AuthenticationContext auth, AIRequest request, RoutingDecision decision, long latencyMs);
    void recordFailure(java.util.UUID requestId, AuthenticationContext auth, AIRequest request, RoutingDecision decision, long latencyMs, Throwable error);
}
