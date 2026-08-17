package com.ai.gateway.security;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.tenant.TenantAccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

/**
 * Central authorization boundary for control-plane and tenant APIs.
 *
 * A platform role does not imply tenant business-data access.
 * Tenant access requires a tenant role plus an exact tenant match.
 */
@Service
public class AuthorizationService {

    public AuthenticationContext requireContext() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticationContext context)) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated principal is required.");
        }

        return context;
    }

    public void requirePlatformRole(SecurityRole... roles) {
        AuthenticationContext context = requireContext();

        if (!context.isPlatformPrincipal()) {
            throw new TenantAccessDeniedException(
                    context.getTenantId(),
                    null);
        }

        requireRole(context, roles);
    }

    public void requireTenantRole(UUID tenantId, SecurityRole... roles) {
        AuthenticationContext context = requireContext();

        if (context.isPlatformPrincipal()
                || context.getTenantId() == null
                || tenantId == null
                || !context.getTenantId().equals(tenantId)) {
            throw new TenantAccessDeniedException(
                    context.getTenantId(),
                    tenantId);
        }

        requireRole(context, roles);
    }

    public UUID requireOwnTenant(SecurityRole... roles) {
        AuthenticationContext context = requireContext();

        if (context.isPlatformPrincipal() || context.getTenantId() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Tenant principal is required.");
        }

        requireRole(context, roles);
        return context.getTenantId();
    }

    private void requireRole(
            AuthenticationContext context,
            SecurityRole... roles) {

        if (roles == null || roles.length == 0) {
            throw new org.springframework.security.access.AccessDeniedException("Authorization role is required.");
        }

        boolean allowed = Arrays.stream(roles)
                .anyMatch(role -> role == context.getRole());

        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Insufficient authorization role: " + context.getRole());
        }
    }
}
