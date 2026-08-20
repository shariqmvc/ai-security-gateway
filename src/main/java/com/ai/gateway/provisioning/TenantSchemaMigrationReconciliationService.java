package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Reconciles tenant schemas that pre-date newly introduced tenant migrations.
 *
 * <p>Tenant schema migrations are authoritative in
 * {@code classpath:db/tenant-migration}. New tenants are already migrated by
 * {@link TenantSchemaProvisioningService}; this runner closes the lifecycle
 * gap for tenants that existed before a new migration was introduced.</p>
 *
 * <p>The operation is idempotent because Flyway tracks migration state inside
 * each tenant schema. Flyway's schema history lock also protects concurrent
 * application instances from applying the same migration twice.</p>
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaMigrationReconciliationService
        implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TenantSchemaMigrationReconciliationService.class);

    private final TenantRepository tenantRepository;
    private final TenantSchemaProvisioningService provisioningService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${gateway.tenant-schema.migration.reconcile-on-startup:true}")
    private boolean reconcileOnStartup;

    @Override
    public void run(ApplicationArguments args) {

        if (!reconcileOnStartup) {
            log.info(
                    "Tenant schema migration reconciliation is disabled.");
            return;
        }

        reconcileAll();
    }

    /**
     * Migrates every existing tenant with a configured schema name to the
     * current tenant migration version.
     */
    public ReconciliationResult reconcileAll() {

        List<Tenant> tenants = tenantRepository.findAll();

        int migrated = 0;
        int skipped = 0;

        for (Tenant tenant : tenants) {

            String schemaName = tenant.getSchemaName();

            if (schemaName == null || schemaName.isBlank()) {
                skipped++;

                log.warn(
                        "Skipping tenant schema reconciliation: "
                                + "tenantId={} tenantCode={} has no schema name.",
                        tenant.getId(),
                        tenant.getTenantCode());

                continue;
            }

            if (!schemaExists(schemaName)) {
                skipped++;

                log.error(
                        "Skipping tenant schema reconciliation: "
                                + "tenantId={} tenantCode={} schema={} does not exist. "
                                + "Tenant metadata references a missing schema; "
                                + "automatic schema creation is intentionally avoided during "
                                + "reconciliation to prevent accidental recreation of a "
                                + "potentially deleted tenant database.",
                        tenant.getId(),
                        tenant.getTenantCode(),
                        schemaName);

                continue;
            }

            log.info(
                    "Reconciling tenant schema: tenantId={} tenantCode={} "
                            + "schema={}",
                    tenant.getId(),
                    tenant.getTenantCode(),
                    schemaName);

            provisioningService.migrateSchema(schemaName);
            migrated++;
        }

        log.info(
                "Tenant schema migration reconciliation completed: "
                        + "tenants={} migrated={} skipped={}",
                tenants.size(),
                migrated,
                skipped);

        return new ReconciliationResult(
                tenants.size(),
                migrated,
                skipped);
    }


    private boolean schemaExists(String schemaName) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.schemata
                WHERE schema_name = ?
                """,
                Integer.class,
                schemaName);

        return count != null && count == 1;
    }

    public record ReconciliationResult(
            int totalTenants,
            int migratedTenants,
            int skippedTenants) {
    }
}
