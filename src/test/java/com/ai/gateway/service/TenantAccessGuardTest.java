package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantAccessGuardTest {

    private final TenantAccessGuard guard =
            new TenantAccessGuard(new AuthorizationService());

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowAuthenticatedTenantToAccessItself() {
        UUID tenantId = UUID.randomUUID();
        setContext(tenantId, SecurityRole.TENANT_OWNER, false);

        assertDoesNotThrow(() -> guard.requireAccess(tenantId));
    }

    @Test
    void shouldRejectAuthenticatedTenantAccessToAnotherTenant() {
        UUID authenticatedTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();
        setContext(authenticatedTenantId, SecurityRole.TENANT_OWNER, false);

        assertThrows(
                TenantAccessDeniedException.class,
                () -> guard.requireAccess(requestedTenantId));
    }

    @Test
    void shouldRejectPlatformPrincipalWithoutTenantContext() {
        UUID requestedTenantId = UUID.randomUUID();
        setContext(null, SecurityRole.PLATFORM_OWNER, true);

        TenantAccessDeniedException ex = assertThrows(
                TenantAccessDeniedException.class,
                () -> guard.requireAccess(requestedTenantId));

        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("not authorized to access tenant-scoped data"));
    }

    @Test
    void shouldAllowTenantUserToAccessOwnTenant() {
        UUID tenantId = UUID.randomUUID();
        setContext(tenantId, SecurityRole.TENANT_USER, false);

        assertDoesNotThrow(() -> guard.requireAccess(tenantId));
    }

    private void setContext(
            UUID tenantId,
            SecurityRole role,
            boolean platformPrincipal) {

        AuthenticationContext context = AuthenticationContext.builder()
                .platformPrincipal(platformPrincipal)
                .tenantId(tenantId)
                .role(role)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        context,
                        null,
                        List.of()));
    }
}
