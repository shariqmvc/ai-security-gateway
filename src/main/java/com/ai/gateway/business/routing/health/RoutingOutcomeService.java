package com.ai.gateway.business.routing.health;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.business.routing.health.entity.RoutingOutcome;

public interface RoutingOutcomeService {
    void recordSuccess(java.util.UUID requestId, AuthenticationContext auth, AIRequest request, RoutingDecision decision, long latencyMs);
    void recordFailure(java.util.UUID requestId, AuthenticationContext auth, AIRequest request, RoutingDecision decision, long latencyMs, Throwable error);
}
