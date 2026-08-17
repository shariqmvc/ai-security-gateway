package com.ai.gateway.tenant;

import java.util.UUID;

/**
 * Raised when an authenticated tenant attempts to access another tenant's
 * tenant-scoped resource.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(
            UUID authenticatedTenantId,
            UUID requestedTenantId) {
        super("Tenant " + authenticatedTenantId
                + " is not authorized to access tenant "
                + requestedTenantId + ".");
    }
}
