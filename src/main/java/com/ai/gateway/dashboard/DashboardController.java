package com.ai.gateway.dashboard;

import com.ai.gateway.dashboard.dto.*;
import com.ai.gateway.dashboard.service.DashboardService;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthorizationService authorizationService;

    /**
     * Platform dashboard overview.
     *
     * Authorization is enforced inside DashboardServiceImpl via
     * AuthorizationService.requirePlatformRole(...).
     */
    @GetMapping("/platform/dashboard/overview")
    public PlatformOverviewResponse platformOverview() {
        return dashboardService.platformOverview();
    }

    /**
     * Platform health.
     *
     * Authorization is enforced inside DashboardServiceImpl.
     */
    @GetMapping("/platform/dashboard/health")
    public HealthResponse platformHealth() {
        return dashboardService.platformHealth();
    }

    /**
     * Platform provider health.
     *
     * Authorization is enforced inside DashboardServiceImpl.
     */
    @GetMapping("/platform/dashboard/providers/{provider}")
    public ProviderResponse platformProvider(
            @PathVariable String provider) {

        return dashboardService.platformProvider(provider);
    }

    /**
     * Tenant dashboard overview.
     *
     * IMPORTANT:
     * Do not use TenantContext.require() here.
     *
     * Personal principals deliberately do not initialize TenantContext.
     * AuthorizationService.requireOwnTenant(...) validates that the
     * authenticated principal is a real tenant principal and has one of
     * the permitted tenant roles before returning the tenant ID.
     */
    @GetMapping("/tenant/dashboard/overview")
    public OverviewResponse tenantOverview() {

        UUID tenantId = authorizationService.requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_OPERATOR,
                SecurityRole.TENANT_AUDITOR
        );

        return dashboardService.tenantOverview(tenantId);
    }

    /**
     * Tenant security dashboard.
     *
     * IMPORTANT:
     * Do not use TenantContext.require() here either.
     *
     * This endpoint has its own role policy, which intentionally differs
     * from tenantOverview().
     */
    @GetMapping("/tenant/dashboard/security")
    public SecurityResponse tenantSecurity() {

        UUID tenantId = authorizationService.requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_AUDITOR
        );

        return dashboardService.tenantSecurity(tenantId);
    }
}