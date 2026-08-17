package com.ai.gateway.dashboard;

import com.ai.gateway.dashboard.dto.*;
import com.ai.gateway.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/platform/dashboard/overview")
    public PlatformOverviewResponse platformOverview() {
        return dashboardService.platformOverview();
    }

    @GetMapping("/platform/dashboard/health")
    public HealthResponse platformHealth() {
        return dashboardService.platformHealth();
    }

    @GetMapping("/platform/dashboard/providers/{provider}")
    public ProviderResponse platformProvider(@PathVariable String provider) {
        return dashboardService.platformProvider(provider);
    }

    @GetMapping("/tenant/dashboard/overview")
    public OverviewResponse tenantOverview() {
        return dashboardService.tenantOverview(
                com.ai.gateway.tenant.TenantContext.require());
    }

    @GetMapping("/tenant/dashboard/security")
    public SecurityResponse tenantSecurity() {
        return dashboardService.tenantSecurity(
                com.ai.gateway.tenant.TenantContext.require());
    }
}
