package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private final AuthorizationService service = new AuthorizationService();

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void platformOwnerDoesNotImplicitlyGetTenantAccess() {
        UUID tenantId = UUID.randomUUID();

        setContext(AuthenticationContext.builder()
                .platformPrincipal(true)
                .role(SecurityRole.PLATFORM_OWNER)
                .build());

        assertThrows(
                TenantAccessDeniedException.class,
                () -> service.requireTenantRole(
                        tenantId,
                        SecurityRole.TENANT_OWNER));
    }

    @Test
    void tenantOwnerCanAccessOwnTenant() {
        UUID tenantId = UUID.randomUUID();

        setContext(AuthenticationContext.builder()
                .platformPrincipal(false)
                .tenantId(tenantId)
                .role(SecurityRole.TENANT_OWNER)
                .build());

        assertDoesNotThrow(() ->
                service.requireTenantRole(
                        tenantId,
                        SecurityRole.TENANT_OWNER,
                        SecurityRole.TENANT_ADMIN));
    }

    @Test
    void tenantOwnerCannotAccessAnotherTenant() {
        UUID ownTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        setContext(AuthenticationContext.builder()
                .platformPrincipal(false)
                .tenantId(ownTenant)
                .role(SecurityRole.TENANT_OWNER)
                .build());

        assertThrows(
                TenantAccessDeniedException.class,
                () -> service.requireTenantRole(
                        otherTenant,
                        SecurityRole.TENANT_OWNER));
    }

    @Test
    void platformAdminCanAccessPlatformScope() {
        setContext(AuthenticationContext.builder()
                .platformPrincipal(true)
                .role(SecurityRole.PLATFORM_ADMIN)
                .build());

        assertDoesNotThrow(() ->
                service.requirePlatformRole(
                        SecurityRole.PLATFORM_OWNER,
                        SecurityRole.PLATFORM_ADMIN));
    }

    @Test
    void tenantUserCannotAccessPlatformScope() {
        setContext(AuthenticationContext.builder()
                .platformPrincipal(false)
                .tenantId(UUID.randomUUID())
                .role(SecurityRole.TENANT_USER)
                .build());

        assertThrows(
                TenantAccessDeniedException.class,
                () -> service.requirePlatformRole(
                        SecurityRole.PLATFORM_OWNER));
    }

    private void setContext(AuthenticationContext context) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        context,
                        null,
                        java.util.List.of()));
    }
}
