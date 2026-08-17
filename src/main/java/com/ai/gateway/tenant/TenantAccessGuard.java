package com.ai.gateway.tenant;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central authorization boundary for tenant-scoped application resources.
 *
 * The tenant identity is derived from the authenticated API-key context
 * established by AuthenticationFilter, never from a client-controlled
 * tenant identifier.
 */
@Component
public class TenantAccessGuard {

    /**
     * Requires the requested tenant to be the authenticated tenant.
     */
    public void requireAccess(UUID requestedTenantId) {
        if (requestedTenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be null.");
        }

        UUID authenticatedTenantId = TenantContext.require();

        if (!authenticatedTenantId.equals(requestedTenantId)) {
            throw new TenantAccessDeniedException(
                    authenticatedTenantId,
                    requestedTenantId);
        }
    }

    /**
     * Returns the tenant established by authentication for the current
     * request.
     */
    public UUID requireAuthenticatedTenant() {
        return TenantContext.require();
    }
}
