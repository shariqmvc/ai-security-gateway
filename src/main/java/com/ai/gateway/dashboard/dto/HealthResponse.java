package com.ai.gateway.dashboard.dto;

import java.util.List;

public record HealthResponse(
        String scope,
        long healthy,
        long degraded,
        long unhealthy,
        List<ProviderHealthItem> providers) {
}
