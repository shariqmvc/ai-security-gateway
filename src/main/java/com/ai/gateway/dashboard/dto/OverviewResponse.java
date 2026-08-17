package com.ai.gateway.dashboard.dto;

import com.ai.gateway.cost.dto.CostSummary;

import java.util.List;
import java.util.UUID;

public record OverviewResponse(
        String scope,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        long requestCount,
        long auditCount,
        CostSummary cost,
        List<ProviderHealthItem> providerHealth) {
}
