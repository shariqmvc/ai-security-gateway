package com.ai.gateway.routing.health;

import com.ai.gateway.enums.Provider;

import java.time.LocalDateTime;

public record RoutingHealthSnapshot(
        Provider provider,
        String model,
        RoutingHealthStatus status,
        long successCount,
        long failureCount,
        long consecutiveFailures,
        double availability,
        double ewmaLatencyMs,
        double p95LatencyMs,
        LocalDateTime lastObservedAt,
        boolean fresh) {
}
