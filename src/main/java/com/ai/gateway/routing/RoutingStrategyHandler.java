package com.ai.gateway.routing;

public interface RoutingStrategyHandler {

    boolean supports(RoutingContext context);

    RoutingDecision route(RoutingContext context);
}
