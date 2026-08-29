package com.ai.gateway.business.cache;

import com.ai.gateway.core.cache.InferenceCacheService;
import com.ai.gateway.core.cache.InferenceCacheStats;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Phase 10 cache operations for controlled invalidation and observability.
 *
 * Tenant invalidation is deliberately restricted to the authenticated tenant
 * or an explicitly authorized platform principal. No cache contents are exposed.
 */
@RestController
@RequiredArgsConstructor
public class InferenceCacheController {

    private final InferenceCacheService cacheService;
    private final AuthorizationService authorizationService;

    @GetMapping("/admin/cache/inference/stats")
    public InferenceCacheStats stats() {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);
        return cacheService.stats();
    }

    @PostMapping("/admin/cache/inference/tenants/{tenantId}/invalidate")
    public InferenceCacheStats invalidateTenant(
            @PathVariable UUID tenantId) {

        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);

        long invalidatedEntries = cacheService.invalidateTenant(tenantId);
        return InferenceCacheStats.tenant(
                cacheService.isEnabled(),
                cacheService.estimatedSize(),
                cacheService.hitCount(),
                cacheService.missCount(),
                cacheService.invalidationCount(),
                invalidatedEntries,
                tenantId);
    }

    @PostMapping("/api/cache/inference/tenant/invalidate")
    public InferenceCacheStats invalidateOwnTenant() {
        UUID tenantId = authorizationService.requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_OPERATOR);

        long invalidatedEntries = cacheService.invalidateTenant(tenantId);
        return InferenceCacheStats.tenant(
                cacheService.isEnabled(),
                cacheService.estimatedSize(),
                cacheService.hitCount(),
                cacheService.missCount(),
                cacheService.invalidationCount(),
                invalidatedEntries,
                tenantId);
    }
}
