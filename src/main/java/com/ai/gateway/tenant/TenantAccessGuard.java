package com.ai.gateway.tenant;

import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central authorization boundary for tenant-scoped application resources.
 *
 * Platform principals are deliberately denied tenant-scoped business data.
 * Tenant principals may access only their authenticated tenant.
 */
@Component
@RequiredArgsConstructor
public class TenantAccessGuard {

    private final AuthorizationService authorizationService;

    /**
     * Requires the requested tenant to be the authenticated tenant.
     * Platform principals are explicitly denied rather than falling through
     * to TenantContext.require(), because platform principals intentionally
     * do not have a tenant context.
     */
    public void requireAccess(UUID requestedTenantId) {
        if (requestedTenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be null.");
        }

        authorizationService.requireTenantRole(
                requestedTenantId,
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_BILLING_ADMIN,
                SecurityRole.TENANT_AUDITOR,
                SecurityRole.TENANT_OPERATOR,
                SecurityRole.TENANT_USER);
    }

    /**
     * Returns the tenant established by authentication for the current
     * request. This remains intentionally tenant-only.
     */
    public UUID requireAuthenticatedTenant() {
        return authorizationService.requireOwnTenant(
                SecurityRole.TENANT_OWNER,
                SecurityRole.TENANT_ADMIN,
                SecurityRole.TENANT_SECURITY_ADMIN,
                SecurityRole.TENANT_BILLING_ADMIN,
                SecurityRole.TENANT_AUDITOR,
                SecurityRole.TENANT_OPERATOR,
                SecurityRole.TENANT_USER);
    }
}
