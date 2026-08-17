package com.ai.gateway.dashboard.service;

import com.ai.gateway.dashboard.dto.HealthResponse;
import com.ai.gateway.dashboard.dto.OverviewResponse;
import com.ai.gateway.dashboard.dto.PlatformOverviewResponse;
import com.ai.gateway.dashboard.dto.ProviderResponse;
import com.ai.gateway.dashboard.dto.SecurityResponse;

import java.util.UUID;

public interface DashboardService {

    OverviewResponse tenantOverview(UUID tenantId);

    SecurityResponse tenantSecurity(UUID tenantId);

    HealthResponse platformHealth();

    PlatformOverviewResponse platformOverview();

    ProviderResponse platformProvider(String provider);
}
