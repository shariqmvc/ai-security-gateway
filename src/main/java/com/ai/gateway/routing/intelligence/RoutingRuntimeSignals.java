package com.ai.gateway.routing.intelligence;

import java.util.Map;

public record RoutingRuntimeSignals(
        Map<String, Double> latencyMs,
        Map<String, Double> availability) {

    public RoutingRuntimeSignals {
        latencyMs = latencyMs == null ? Map.of() : Map.copyOf(latencyMs);
        availability = availability == null ? Map.of() : Map.copyOf(availability);
    }

    public static RoutingRuntimeSignals empty() {
        return new RoutingRuntimeSignals(Map.of(), Map.of());
    }
}
