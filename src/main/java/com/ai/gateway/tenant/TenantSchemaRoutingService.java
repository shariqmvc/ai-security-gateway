package com.ai.gateway.tenant;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantSchemaRoutingService {

    private final EntityManager entityManager;
    private final TenantRepository tenantRepository;

    /**
     * Routes the current transaction using the authenticated tenant context.
     * Intended for request-scoped operational writes where the authentication
     * filter has already initialized TenantSchemaContext.
     */
    public void useTenantSchema() {
        useTenantSchema(TenantSchemaContext.require());
    }

    /**
     * Routes the current transaction using the persisted schema for the
     * supplied tenant. This is preferred by service methods that already have
     * a tenant id and is safe for admin/concurrency flows where the request
     * ThreadLocal context is not present.
     */
    @Transactional(readOnly = true)
    public void useTenantSchema(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be null.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        if (tenant.getSchemaName() == null
                || tenant.getSchemaName().isBlank()) {
            throw new IllegalStateException(
                    "Tenant schema name is not configured: " + tenantId);
        }

        useTenantSchema(tenant.getSchemaName());
    }

    private void useTenantSchema(String schemaName) {
        if (!schemaName.matches(
                "[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid tenant schema name: " + schemaName);
        }

        entityManager.createNativeQuery(
                "SET LOCAL search_path TO \"" + schemaName + "\"")
                .executeUpdate();
    }
}
