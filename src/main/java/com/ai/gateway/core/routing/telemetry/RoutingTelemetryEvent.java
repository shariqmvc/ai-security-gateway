package com.ai.gateway.core.routing.telemetry;

import java.time.Instant;
import java.util.UUID;

/**
 * Product-neutral execution telemetry for an LLM routing attempt.
 *
 * <p>Only operational metadata is captured. Prompt, response content,
 * credentials and tenant-private payloads are deliberately excluded.</p>
 */
public record RoutingTelemetryEvent(
        UUID requestId,
        String provider,
        String model,
        String endpointId,
        boolean success,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer reasoningTokens,
        String failureCategory,
        Instant recordedAt) {

    public RoutingTelemetryEvent {
        latencyMs = Math.max(0L, latencyMs);
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    public String candidateKey() {
        String base = provider + ":" + model;
        return endpointId == null || endpointId.isBlank()
                ? base
                : base + "@" + endpointId;
    }
}
