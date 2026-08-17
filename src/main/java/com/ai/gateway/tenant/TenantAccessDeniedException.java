package com.ai.gateway.tenant;

import java.util.UUID;

/**
 * Raised when an authenticated principal attempts to access a tenant-scoped
 * resource outside its authorized tenant boundary.
 */
public class TenantAccessDeniedException extends RuntimeException {

    public TenantAccessDeniedException(
            UUID authenticatedTenantId,
            UUID requestedTenantId) {
        super(buildMessage(authenticatedTenantId, requestedTenantId));
    }

    private static String buildMessage(
            UUID authenticatedTenantId,
            UUID requestedTenantId) {

        if (authenticatedTenantId == null) {
            return "The authenticated principal is not authorized to access tenant-scoped data.";
        }

        if (requestedTenantId == null) {
            return "The authenticated tenant is not authorized to access this resource.";
        }

        return "Tenant " + authenticatedTenantId
                + " is not authorized to access tenant "
                + requestedTenantId + ".";
    }
}
