package com.ai.gateway.tenant;

import com.ai.gateway.provisioning.TenantSchemaNameResolver;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantSchemaRoutingService {

    private static final Object APPLIED_SCHEMA_RESOURCE =
            TenantSchemaRoutingService.class.getName() + ".APPLIED_SCHEMA";

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
         * Fast path for authenticated request flows. AuthenticationFilter has
         * already established the tenant id and schema together. Validate the
         * deterministic schema mapping before reusing it, but do not perform
         * another control-plane tenant SELECT on every quota/budget operation.
         */
        UUID authenticatedTenantId = TenantContext.get();
        String authenticatedSchema = TenantSchemaContext.get();

        if (authenticatedTenantId == null && authenticatedSchema != null) {
            throw new IllegalStateException(
                    "Tenant schema context exists without an authenticated tenant.");
        }

        if (authenticatedTenantId != null) {
            if (!authenticatedTenantId.equals(tenantId)) {
                throw new TenantAccessDeniedException(
                        authenticatedTenantId,
                        tenantId);
            }

            if (authenticatedSchema == null || authenticatedSchema.isBlank()) {
                throw new IllegalStateException(
                        "Authenticated tenant schema context is missing.");
            }

            String expectedSchema = schemaNameResolver.resolve(
                    Tenant.builder().id(tenantId).build());

            if (!expectedSchema.equals(authenticatedSchema)) {
                throw new IllegalStateException(
                        "Authenticated tenant schema context is invalid.");
            }

            useTenantSchema(authenticatedSchema);
            return;
        }

        // Admin/background/concurrency flows have no authenticated request
        // context, so retain the persisted lookup and schema-identity check.
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        if (tenant.getSchemaName() == null
                || tenant.getSchemaName().isBlank()) {
            throw new IllegalStateException(
                    "Tenant schema name is not configured: " + tenantId);
        }

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

        // SET LOCAL is transaction-scoped. Avoid issuing the same database
        // command repeatedly within one transaction.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            Object applied = TransactionSynchronizationManager.getResource(
                    APPLIED_SCHEMA_RESOURCE);
            if (schemaName.equals(applied)) {
                return;
            }

            // A resource with a different schema belongs to the current
            // transaction only if it was explicitly registered by this
            // service. Replace it before applying the requested schema.
            // The resource is transaction-scoped and is removed again in
            // afterCompletion below, so it cannot leak between pooled threads.
            if (applied != null) {
                TransactionSynchronizationManager.unbindResource(
                        APPLIED_SCHEMA_RESOURCE);
            }
        } else if (TransactionSynchronizationManager.hasResource(
                APPLIED_SCHEMA_RESOURCE)) {
            // Defensive cleanup for threads that may have executed older
            // versions of this service, which could leave the marker bound.
            TransactionSynchronizationManager.unbindResource(
                    APPLIED_SCHEMA_RESOURCE);
        }

        entityManager.createNativeQuery(
                "SET LOCAL search_path TO \"" + schemaName + "\""
        ).executeUpdate();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.bindResource(
                    APPLIED_SCHEMA_RESOURCE,
                    schemaName);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (TransactionSynchronizationManager.hasResource(
                                    APPLIED_SCHEMA_RESOURCE)) {
                                TransactionSynchronizationManager.unbindResource(
                                        APPLIED_SCHEMA_RESOURCE);
                            }
                        }
                    });
        }
    }
}
