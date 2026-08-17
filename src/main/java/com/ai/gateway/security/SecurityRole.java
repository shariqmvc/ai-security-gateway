package com.ai.gateway.security;

/**
 * Authorization roles used by both platform and tenant principals.
 *
 * Platform roles never imply access to tenant business data.
 * Tenant roles are always evaluated against the authenticated tenant.
 */
public enum SecurityRole {
    PLATFORM_OWNER,
    PLATFORM_ADMIN,
    PLATFORM_OPERATIONS,
    PLATFORM_SECURITY_ADMIN,
    PLATFORM_AUDITOR,
    PLATFORM_SUPPORT,

    TENANT_OWNER,
    TENANT_ADMIN,
    TENANT_SECURITY_ADMIN,
    TENANT_BILLING_ADMIN,
    TENANT_AUDITOR,
    TENANT_OPERATOR,
    TENANT_USER;

    public boolean isPlatformRole() {
        return name().startsWith("PLATFORM_");
    }

    public boolean isTenantRole() {
        return name().startsWith("TENANT_");
    }
}
