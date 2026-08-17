package com.ai.gateway.tenant;

import java.util.UUID;

/**
 * Request/transaction scoped tenant schema context.
 *
 * The tenant id is stored alongside the schema so callers can safely reuse
 * the authenticated schema without performing another control-plane tenant
 * lookup. A schema is only reused when it belongs to the same tenant id.
 */
public final class TenantSchemaContext {

    private static final ThreadLocal<String> CURRENT_SCHEMA =
            new ThreadLocal<>();

    private static final ThreadLocal<UUID> CURRENT_TENANT_ID =
            new ThreadLocal<>();

    private TenantSchemaContext() {
    }

    /**
     * Legacy/schema-only setter for administrative flows.
     */
    public static void set(String schemaName) {
        set(null, schemaName);
    }

    /**
     * Sets the authenticated tenant and its schema together.
     */
    public static void set(UUID tenantId, String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant schema name cannot be null or blank.");
        }

        CURRENT_TENANT_ID.set(tenantId);
        CURRENT_SCHEMA.set(schemaName);
    }

    public static String get() {
        return CURRENT_SCHEMA.get();
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * Returns the current schema only when it was initialized for the
     * requested tenant. This prevents schema reuse across tenants.
     */
    public static String getForTenant(UUID tenantId) {
        UUID currentTenantId = CURRENT_TENANT_ID.get();
        String schema = CURRENT_SCHEMA.get();

        if (tenantId == null || currentTenantId == null
                || !tenantId.equals(currentTenantId)
                || schema == null || schema.isBlank()) {
            return null;
        }

        return schema;
    }

    public static String require() {
        String schemaName = CURRENT_SCHEMA.get();

        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalStateException(
                    "Tenant schema context is not initialized.");
        }

        return schemaName;
    }

    public static void clear() {
        CURRENT_SCHEMA.remove();
        CURRENT_TENANT_ID.remove();
    }
}
