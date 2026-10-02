package com.ai.gateway.core.routing.telemetry;

import java.util.List;
import java.util.Map;

/**
 * Snapshot of bounded in-process routing execution telemetry.
 */
public record RoutingTelemetrySnapshot(
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        long totalLatencyMs,
        double averageLatencyMs,
        Map<String, Long> executionsByCandidate,
        Map<String, Long> failuresByCategory,
        List<RoutingTelemetryEvent> recentEvents) {

    public RoutingTelemetrySnapshot {
        executionsByCandidate = executionsByCandidate == null
                ? Map.of()
                : Map.copyOf(executionsByCandidate);
        failuresByCategory = failuresByCategory == null
                ? Map.of()
                : Map.copyOf(failuresByCategory);
        recentEvents = recentEvents == null
                ? List.of()
                : List.copyOf(recentEvents);
    }
}
