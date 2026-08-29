package com.ai.gateway.dashboard.service.impl;

import com.ai.gateway.core.cost.dto.CostSummary;
import com.ai.gateway.business.cost.service.CostService;
import com.ai.gateway.dashboard.dto.*;
import com.ai.gateway.dashboard.service.DashboardService;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.repository.RequestAuditRepository;
import com.ai.gateway.core.routing.health.RoutingHealthService;
import com.ai.gateway.core.routing.health.RoutingHealthSnapshot;
import com.ai.gateway.core.routing.health.RoutingHealthStatus;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CostService costService;
    private final RequestAuditRepository requestAuditRepository;
    private final RoutingHealthService routingHealthService;
    private final TenantRepository tenantRepository;
    private final AuthorizationService authorizationService;

    @Override
    @Transactional(readOnly = true)
    public OverviewResponse tenantOverview(UUID tenantId) {
        authorizationService.requireTenantRole(
                tenantId,
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_OPERATOR,
                SecurityRole.TENANT_AUDITOR);

        var context = authorizationService.requireContext();
        CostSummary cost = costService.getOverallSummary();
        long audits = requestAuditRepository.count();

        return new OverviewResponse(
                "TENANT",
                tenantId,
                context.getTenantCode(),
                context.getTenantName(),
                cost == null || cost.getTotalRequests() == null ? 0 : cost.getTotalRequests(),
                audits,
                cost,
                providerHealth());
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityResponse tenantSecurity(UUID tenantId) {
        authorizationService.requireTenantRole(
                tenantId,
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_AUDITOR);

        long total = requestAuditRepository.count();
        long success = requestAuditRepository.countByStatus(AuditStatus.SUCCESS);
        long failed = requestAuditRepository.countByStatus(AuditStatus.FAILED);

        return new SecurityResponse(
                "TENANT",
                total,
                success,
                failed);
    }

    @Override
    public PlatformOverviewResponse platformOverview() {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS,
                SecurityRole.PLATFORM_AUDITOR);

        return new PlatformOverviewResponse(
                "PLATFORM",
                tenantRepository.count(),
                tenantRepository.countByStatus(com.ai.gateway.tenant.TenantStatus.ACTIVE),
                tenantRepository.countByStatus(com.ai.gateway.tenant.TenantStatus.REQUESTED),
                tenantRepository.countByStatus(com.ai.gateway.tenant.TenantStatus.SUSPENDED),
                providerHealth());
    }

    @Override
    public HealthResponse platformHealth() {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS,
                SecurityRole.PLATFORM_AUDITOR);

        List<ProviderHealthItem> items = providerHealth();

        long healthy = items.stream()
                .filter(item -> item.status() == RoutingHealthStatus.HEALTHY)
                .count();
        long degraded = items.stream()
                .filter(item -> item.status() == RoutingHealthStatus.DEGRADED)
                .count();
        long unhealthy = items.size() - healthy - degraded;

        return new HealthResponse(
                "PLATFORM",
                healthy,
                degraded,
                unhealthy,
                items);
    }

    @Override
    public ProviderResponse platformProvider(String provider) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS,
                SecurityRole.PLATFORM_AUDITOR);

        Provider target = Provider.valueOf(provider.toUpperCase());

        List<RoutingHealthSnapshot> snapshots = routingHealthService.snapshots();

        long healthy = snapshots.stream()
                .filter(s -> s.provider() == target
                        && s.status() == RoutingHealthStatus.HEALTHY)
                .count();
        long degraded = snapshots.stream()
                .filter(s -> s.provider() == target
                        && s.status() == RoutingHealthStatus.DEGRADED)
                .count();
        long unhealthy = snapshots.stream()
                .filter(s -> s.provider() == target
                        && s.status() == RoutingHealthStatus.UNHEALTHY)
                .count();

        return new ProviderResponse(target, healthy, degraded, unhealthy);
    }

    private List<ProviderHealthItem> providerHealth() {
        return routingHealthService.snapshots().stream()
                .map(s -> new ProviderHealthItem(
                        s.provider(),
                        s.model(),
                        s.status(),
                        s.availability(),
                        s.ewmaLatencyMs(),
                        s.p95LatencyMs(),
                        s.fresh()))
                .toList();
    }
}
