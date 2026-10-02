package com.ai.gateway.core.routing.intelligence;

import java.util.Map;

public record RoutingCapacitySignals(
        Map<String, Integer> inFlight,
        Map<String, Double> requestsPerMinute,
        Map<String, Double> tokensPerMinute,
        Map<String, Double> utilization) {

    public RoutingCapacitySignals {
        inFlight = inFlight == null ? Map.of() : Map.copyOf(inFlight);
        requestsPerMinute = requestsPerMinute == null ? Map.of() : Map.copyOf(requestsPerMinute);
        tokensPerMinute = tokensPerMinute == null ? Map.of() : Map.copyOf(tokensPerMinute);
        utilization = utilization == null ? Map.of() : Map.copyOf(utilization);
    }

    public static RoutingCapacitySignals empty() {
        return new RoutingCapacitySignals(Map.of(), Map.of(), Map.of(), Map.of());
    }
}
