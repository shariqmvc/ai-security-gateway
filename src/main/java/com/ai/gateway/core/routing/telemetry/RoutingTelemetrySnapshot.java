package com.ai.gateway.core.routing.telemetry;

import java.util.List;
import java.util.Map;

public record RoutingTelemetrySnapshot(
        long totalExecutions,
        long successfulExecutions,
        long failedExecutions,
        long totalLatencyMs,
        double averageLatencyMs,
        Map<String, Long> executionsByCandidate,
        Map<String, Long> failuresByCategory,
        Map<String, CandidateTelemetryStats> candidateStats,
        List<RoutingTelemetryEvent> recentEvents) {

    public RoutingTelemetrySnapshot {
        executionsByCandidate = executionsByCandidate == null ? Map.of() : Map.copyOf(executionsByCandidate);
        failuresByCategory = failuresByCategory == null ? Map.of() : Map.copyOf(failuresByCategory);
        candidateStats = candidateStats == null ? Map.of() : Map.copyOf(candidateStats);
        recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
    }

    public record CandidateTelemetryStats(
            long executions,
            long successes,
            long failures,
            double successRate,
            double averageLatencyMs,
            double p50LatencyMs,
            double p95LatencyMs,
            double averageInputTokens,
            double averageOutputTokens,
            double averageTotalTokens,
            double averageReasoningTokens,
            double outputTokensPerSecond,
            Map<String, Long> finishReasons,
            Map<String, Long> failureCategories) {

        public CandidateTelemetryStats {
            finishReasons = finishReasons == null ? Map.of() : Map.copyOf(finishReasons);
            failureCategories = failureCategories == null ? Map.of() : Map.copyOf(failureCategories);
        }
    }
}
