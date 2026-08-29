package com.ai.gateway.service;

import com.ai.gateway.business.cache.InferenceCacheController;
import com.ai.gateway.core.cache.InferenceCacheService;
import com.ai.gateway.core.cache.InferenceCacheStats;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class InferenceCacheControllerTest {

    @Test
    void platformCanInvalidateSpecificTenant() {
        InferenceCacheService cacheService = mock(InferenceCacheService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        InferenceCacheController controller =
                new InferenceCacheController(cacheService, authorizationService);
        UUID tenantId = UUID.randomUUID();

        when(cacheService.isEnabled()).thenReturn(true);
        when(cacheService.estimatedSize()).thenReturn(4L);
        when(cacheService.hitCount()).thenReturn(8L);
        when(cacheService.missCount()).thenReturn(2L);
        when(cacheService.invalidationCount()).thenReturn(1L);
        when(cacheService.invalidateTenant(tenantId)).thenReturn(2L);

        InferenceCacheStats result = controller.invalidateTenant(tenantId);

        verify(authorizationService).requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);
        verify(cacheService).invalidateTenant(tenantId);
        assertEquals(tenantId, result.tenantId());
        assertEquals(8L, result.hitCount());
        assertEquals(2L, result.missCount());
        assertEquals(0.8d, result.hitRate());
        assertEquals(2L, result.invalidatedEntries());
    }

    @Test
    void platformCanReadCacheStats() {
        InferenceCacheService cacheService = mock(InferenceCacheService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        InferenceCacheController controller =
                new InferenceCacheController(cacheService, authorizationService);
        InferenceCacheStats expected = new InferenceCacheStats(
                true, 3L, 9L, 1L, 0.9d, 0L, 0L, null);

        when(cacheService.stats()).thenReturn(expected);

        assertSame(expected, controller.stats());
        verify(authorizationService).requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);
        verify(cacheService).stats();
    }

    @Test
    void tenantCanInvalidateOwnCache() {
        InferenceCacheService cacheService = mock(InferenceCacheService.class);
        AuthorizationService authorizationService = mock(AuthorizationService.class);
        InferenceCacheController controller =
                new InferenceCacheController(cacheService, authorizationService);
        UUID tenantId = UUID.randomUUID();

        when(authorizationService.requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_OPERATOR)).thenReturn(tenantId);
        when(cacheService.isEnabled()).thenReturn(true);
        when(cacheService.estimatedSize()).thenReturn(1L);
        when(cacheService.hitCount()).thenReturn(1L);
        when(cacheService.missCount()).thenReturn(1L);
        when(cacheService.invalidationCount()).thenReturn(1L);

        InferenceCacheStats result = controller.invalidateOwnTenant();

        verify(authorizationService).requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_OPERATOR);
        verify(cacheService).invalidateTenant(tenantId);
        assertEquals(tenantId, result.tenantId());
        assertEquals(0.5d, result.hitRate());
        assertEquals(0L, result.invalidatedEntries());
    }
}
