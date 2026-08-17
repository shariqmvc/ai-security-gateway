package com.ai.gateway.dashboard.dto;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.health.RoutingHealthStatus;

public record ProviderHealthItem(
        Provider provider,
        String model,
        RoutingHealthStatus status,
        double availability,
        double ewmaLatencyMs,
        double p95LatencyMs,
        boolean fresh) {
}
