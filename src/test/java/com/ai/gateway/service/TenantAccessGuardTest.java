package com.ai.gateway.service;

import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAccessGuardTest {

    private final TenantAccessGuard guard = new TenantAccessGuard();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void shouldAllowAuthenticatedTenantToAccessItself() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        assertDoesNotThrow(() -> guard.requireAccess(tenantId));
    }

    @Test
    void shouldRejectAuthenticatedTenantAccessToAnotherTenant() {
        UUID authenticatedTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();
        TenantContext.set(authenticatedTenantId);

        assertThrows(
                TenantAccessDeniedException.class,
                () -> guard.requireAccess(requestedTenantId));
    }
}
