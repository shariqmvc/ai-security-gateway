package com.ai.gateway.core.routing.telemetry;

import java.time.Instant;
import java.util.UUID;

public record RoutingTelemetryEvent(
        UUID requestId,
        String provider,
        String model,
        String endpointId,
        boolean success,
        long latencyMs,
        Long timeToFirstTokenMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer reasoningTokens,
        String finishReason,
        String failureCategory,
        Instant recordedAt) {

    public RoutingTelemetryEvent {
        latencyMs = Math.max(0L, latencyMs);
        timeToFirstTokenMs = timeToFirstTokenMs == null
                ? null
                : Math.max(0L, timeToFirstTokenMs);
        inputTokens = nonNegative(inputTokens);
        outputTokens = nonNegative(outputTokens);
        totalTokens = totalTokens == null && inputTokens != null && outputTokens != null
                ? safeAdd(inputTokens, outputTokens)
                : nonNegative(totalTokens);
        reasoningTokens = nonNegative(reasoningTokens);
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    public String candidateKey() {
        String base = provider + "/" + model;
        return endpointId == null || endpointId.isBlank() ? base : base + "@" + endpointId;
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    private static Integer safeAdd(int left, int right) {
        long value = (long) left + right;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
