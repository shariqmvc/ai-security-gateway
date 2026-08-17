package com.ai.gateway.tenant;

import com.ai.gateway.provisioning.TenantSchemaNameResolver;
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
    private final TenantSchemaNameResolver schemaNameResolver;

    /**
     * Routes the current transaction using the authenticated tenant context.
     * Intended for request-scoped operational writes where the authentication
     * filter has already initialized TenantSchemaContext.
     */
    public void useTenantSchema() {
        UUID tenantId = TenantContext.require();
        String schemaName = TenantSchemaContext.require();

        String expectedSchema = schemaNameResolver.resolve(
                Tenant.builder().id(tenantId).build());

        if (!expectedSchema.equals(schemaName)) {
            throw new IllegalStateException(
                    "Tenant schema context does not match authenticated tenant.");
        }

        useTenantSchema(schemaName);
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

        /*
         * Defense in depth: an authenticated tenant request may only route
         * to its own schema. Calls made by trusted admin/background flows
         * have no TenantContext and may continue to use an explicit tenantId.
         */
        UUID authenticatedTenantId = TenantContext.get();
        String authenticatedSchema = TenantSchemaContext.get();

        // A partially initialized request context is never allowed to route
        // tenant operational data. This closes the "stale schema only" case
        // where a thread could carry a schema without its tenant identity.
        if (authenticatedTenantId == null && authenticatedSchema != null) {
            throw new IllegalStateException(
                    "Tenant schema context exists without an authenticated tenant.");
        }

        if (authenticatedTenantId != null
                && !authenticatedTenantId.equals(tenantId)) {
            throw new TenantAccessDeniedException(
                    authenticatedTenantId,
                    tenantId);
        }

        if (authenticatedTenantId != null
                && authenticatedSchema != null) {

            Tenant authenticatedTenant = tenantRepository
                    .findById(authenticatedTenantId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Authenticated tenant not found: "
                                    + authenticatedTenantId));

            String expectedAuthenticatedSchema =
                    schemaNameResolver.resolve(authenticatedTenant);

            if (!expectedAuthenticatedSchema.equals(authenticatedSchema)) {
                throw new IllegalStateException(
                        "Authenticated tenant schema context is invalid.");
            }
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        if (tenant.getSchemaName() == null
                || tenant.getSchemaName().isBlank()) {
            throw new IllegalStateException(
                    "Tenant schema name is not configured: " + tenantId);
        }

        // The tenant schema is derived from the tenant UUID during
        // provisioning. Refuse metadata that points a tenant at another
        // tenant's physical schema; otherwise a corrupted/misconfigured
        // control-plane row could bypass the logical tenant boundary.
        String expectedSchema = schemaNameResolver.resolve(tenant);
        if (!expectedSchema.equals(tenant.getSchemaName())) {
            throw new IllegalStateException(
                    "Tenant schema does not match tenant identity: " + tenantId);
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
