package com.ai.gateway.core.routing.intelligence;

import java.util.Map;

public record RoutingRuntimeSignals(
        Map<String, Double> latencyMs,
        Map<String, Double> availability,
        Map<String, Long> observations,
        RoutingCapacitySignals capacity) {

    public RoutingRuntimeSignals(
            Map<String, Double> latencyMs,
            Map<String, Double> availability) {
        this(latencyMs, availability, Map.of(), RoutingCapacitySignals.empty());
    }

    public RoutingRuntimeSignals(
            Map<String, Double> latencyMs,
            Map<String, Double> availability,
            Map<String, Long> observations) {
        this(latencyMs, availability, observations, RoutingCapacitySignals.empty());
    }

    public RoutingRuntimeSignals {
        latencyMs = latencyMs == null ? Map.of() : Map.copyOf(latencyMs);
        availability = availability == null ? Map.of() : Map.copyOf(availability);
        observations = observations == null ? Map.of() : Map.copyOf(observations);
        capacity = capacity == null ? RoutingCapacitySignals.empty() : capacity;
    }

    public static RoutingRuntimeSignals empty() {
        return new RoutingRuntimeSignals(
                Map.of(), Map.of(), Map.of(), RoutingCapacitySignals.empty());
    }
}
