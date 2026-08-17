package com.ai.gateway.tenant;

public final class TenantSchemaContext {

    private static final ThreadLocal<String> CURRENT_SCHEMA =
            new ThreadLocal<>();

    private TenantSchemaContext() {
    }

    public static void set(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant schema name cannot be null or blank.");
        }

        CURRENT_SCHEMA.set(schemaName);
    }

    public static String get() {
        return CURRENT_SCHEMA.get();
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
    }
}