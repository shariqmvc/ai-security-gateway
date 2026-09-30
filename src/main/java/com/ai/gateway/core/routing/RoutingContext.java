package com.ai.gateway.core.routing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.cost.routing.RoutingCostContext;

public record RoutingContext(
        ChatRequest request,
        AuthenticationContext authenticationContext,
        RoutingCostContext costContext) {

    public RoutingContext(
            ChatRequest request,
            AuthenticationContext authenticationContext) {
        this(request, authenticationContext, null);
    }

    /**
     * Canonical routing view of the gateway request. Keeping this derived from
     * ChatRequest avoids duplicating request state while giving the routing
     * engine a stable domain contract.
     */
    public RoutingRequest routingRequest() {
        return RoutingRequest.from(request);
    }

    public RoutingCostContext effectiveCostContext() {
        if (costContext != null) {
            return costContext;
        }
        if (request == null) {
            return RoutingCostContext.requestOnly(null);
        }
        return new RoutingCostContext(
                request.getMaximumRequestCost(),
                request.getRemainingWorkflowBudget());
    }
}
