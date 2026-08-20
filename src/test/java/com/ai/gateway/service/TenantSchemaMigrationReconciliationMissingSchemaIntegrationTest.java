package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provisioning.TenantSchemaMigrationReconciliationService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TenantSchemaMigrationReconciliationMissingSchemaIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantSchemaMigrationReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;

    @AfterEach
    void cleanup() {
        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void shouldSkipTenantWhosePersistedSchemaDoesNotExist() {

        Tenant tenant = Tenant.builder()
                .tenantCode("RAG-MISSING-SCHEMA-" + UUID.randomUUID())
                .tenantName("Missing Schema Tenant")
                .schemaName("tenant_missing_" + UUID.randomUUID().toString().replace("-", ""))
                .status(TenantStatus.ACTIVE)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        tenant = tenantRepository.saveAndFlush(tenant);
        tenantId = tenant.getId();

        TenantSchemaMigrationReconciliationService.ReconciliationResult result =
                reconciliationService.reconcileAll();

        assertTrue(result.skippedTenants() >= 1);
        assertTrue(result.migratedTenants() >= 0);

        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                tenant.getSchemaName());

        assertTrue(schemaCount != null && schemaCount == 0,
                "Reconciliation must not recreate a missing tenant schema");
    }
}
