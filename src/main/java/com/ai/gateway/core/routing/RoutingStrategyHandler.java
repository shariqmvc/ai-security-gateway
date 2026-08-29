package com.ai.gateway.core.routing;

public interface RoutingStrategyHandler {

    boolean supports(RoutingContext context);

    RoutingDecision route(RoutingContext context);
}
