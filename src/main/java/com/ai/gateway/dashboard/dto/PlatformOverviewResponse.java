package com.ai.gateway.dashboard.dto;

import java.util.List;

public record PlatformOverviewResponse(
        String scope,
        long tenantCount,
        long activeTenantCount,
        long requestedTenantCount,
        long suspendedTenantCount,
        List<ProviderHealthItem> providerHealth) {
}
