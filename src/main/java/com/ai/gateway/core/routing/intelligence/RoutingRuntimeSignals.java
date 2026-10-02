package com.ai.gateway.core.routing.intelligence;

import java.util.Map;

public record RoutingRuntimeSignals(
        Map<String, Double> latencyMs,
        Map<String, Double> availability,
        Map<String, Long> observations) {

    public RoutingRuntimeSignals(Map<String, Double> latencyMs, Map<String, Double> availability) {
        this(latencyMs, availability, Map.of());
    }

    public RoutingRuntimeSignals {
        latencyMs = latencyMs == null ? Map.of() : Map.copyOf(latencyMs);
        availability = availability == null ? Map.of() : Map.copyOf(availability);
        observations = observations == null ? Map.of() : Map.copyOf(observations);
    }

    public static RoutingRuntimeSignals empty() {
        return new RoutingRuntimeSignals(Map.of(), Map.of(), Map.of());
    }
}
