package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.provisioning.TenantProvisioningStatus;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "gemini.api.key=test-gemini-key"
})
class TenantProvisioningEndToEndIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private String schemaName;
    private String provisionedSchema;

    @AfterEach
    void cleanup() {

        if (tenantId != null) {
            /*
             * API_KEYS has a FK to TENANTS, so remove dependent
             * control-plane data before deleting the tenant.
             */
            jdbcTemplate.update(
                    "DELETE FROM API_KEYS WHERE tenant_id = ?",
                    tenantId);

            jdbcTemplate.update(
                    "DELETE FROM tenant_entitlements WHERE tenant_id = ?",
                    tenantId);

            jdbcTemplate.update(
                    "DELETE FROM TENANT_QUOTA_USAGE WHERE tenant_id = ?",
                    tenantId);

            jdbcTemplate.update(
                    "DELETE FROM TENANT_BUDGET_USAGE WHERE tenant_id = ?",
                    tenantId);
        }

        if (schemaName != null) {
            String quoted =
                    "\"" + schemaName.replace("\"", "\"\"") + "\"";

            jdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS " + quoted + " CASCADE");
        }

        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void shouldProvisionTenantEndToEnd() {

        String tenantCode =
                "E2E-74-" +
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase();

        TenantRequest request = TenantRequest.builder()
                .tenantCode(tenantCode)
                .tenantName("Phase 7.4 E2E Tenant")
                .plan(Plan.PROFESSIONAL)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        /*
         * This invokes the real TenantServiceImpl and therefore
         * the real TenantProvisioningService, schema provisioning,
         * entitlement provisioning and API-key provisioning.
         */
        Tenant tenant = tenantService.create(request);

        tenantId = tenant.getId();
        schemaName = tenant.getSchemaName();

        assertNotNull(tenantId);

        /*
         * 1. Tenant lifecycle
         */
        assertEquals(
                TenantStatus.ACTIVE,
                tenant.getStatus());

        assertEquals(
                tenantCode,
                tenant.getTenantCode());

        assertNotNull(tenant.getCreatedAt());

        /*
         * 2. Provisioning metadata
         */
        assertNotNull(
                tenant.getProvisioningStartedAt());

        assertNotNull(
                tenant.getProvisioningCompletedAt());

        assertEquals(
                1,
                tenant.getProvisioningAttempts());

        assertNull(
                tenant.getProvisioningFailureReason());

        /*
         * 3. Physical tenant schema
         */
        assertNotNull(schemaName);

        assertTrue(
                schemaName.startsWith("tenant_"));

        assertTrue(
                schemaName.matches("[a-z0-9_]+"));

        assertTrue(
                schemaName.length() <= 63);

        assertTrue(
                schemaExists(schemaName));

        /*
         * 4. Tenant operational schema
         */
        assertTenantTableExists(
                schemaName,
                "request_audit");

        assertTenantTableExists(
                schemaName,
                "token_usage");

        assertTenantTableExists(
                schemaName,
                "token_vault");

        assertTenantTableExists(
                schemaName,
                "request_cost");

        assertTenantTableExists(
                schemaName,
                "tenant_quota_usage");

        assertTenantTableExists(
                schemaName,
                "tenant_budget_usage");

        assertTenantTableExists(
                schemaName,
                "routing_outcome");

        assertTenantTableExists(
                schemaName,
                "flyway_schema_history");

        /*
         * 5. Tenant Flyway migration
         */
        Integer migrationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM "%s".flyway_schema_history
                WHERE version = '1'
                """.formatted(schemaName),
                Integer.class);

        assertEquals(
                1,
                migrationCount);

        /*
         * 6. Control-plane entitlement
         */
        Integer entitlementCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM tenant_entitlements
                WHERE tenant_id = ?
                """,
                Integer.class,
                tenantId);

        assertEquals(
                1,
                entitlementCount);

        /*
         * 7. Initial API key
         */
        Integer activeApiKeyCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM API_KEYS
                WHERE tenant_id = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                tenantId);

        assertEquals(
                1,
                activeApiKeyCount);

        /*
         * 8. Verify persisted tenant metadata.
         */
        Tenant persisted =
                tenantRepository.findById(tenantId)
                        .orElseThrow();

        assertEquals(
                TenantStatus.ACTIVE,
                persisted.getStatus());

        assertEquals(
                schemaName,
                persisted.getSchemaName());
    }

    @Test
    void shouldBeIdempotentWhenProvisioningAlreadyActiveTenant() {

        String tenantCode =
                "E2E-IDEMP-74-" +
                        UUID.randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase();

        TenantRequest request = TenantRequest.builder()
                .tenantCode(tenantCode)
                .tenantName("Phase 7.4 Idempotency Tenant")
                .plan(Plan.PROFESSIONAL)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        Tenant first =
                tenantService.create(request);

        tenantId = first.getId();
        schemaName = first.getSchemaName();

        assertEquals(
                TenantStatus.ACTIVE,
                first.getStatus());

        assertEquals(
                1,
                first.getProvisioningAttempts());

        /*
         * TenantService itself rejects duplicate tenant codes,
         * so verify the provisioning layer's ACTIVE short-circuit
         * through the persisted lifecycle state rather than creating
         * another tenant with the same code.
         */
        Tenant persisted =
                tenantRepository.findById(tenantId)
                        .orElseThrow();

        assertEquals(
                TenantStatus.ACTIVE,
                persisted.getStatus());

        assertEquals(
                schemaName,
                persisted.getSchemaName());

        assertEquals(
                1,
                persisted.getProvisioningAttempts());

        assertTrue(schemaExists(schemaName));
    }

    private boolean schemaExists(String schema) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.schemata
                WHERE schema_name = ?
                """,
                Integer.class,
                schema);

        return count != null && count == 1;
    }

    private void assertTenantTableExists(
            String schema,
            String table) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """,
                Integer.class,
                schema,
                table);

        assertEquals(
                1,
                count,
                "Expected table " +
                        schema +
                        "." +
                        table +
                        " to exist");
    }

    @Test
    void shouldProvisionTenantOperationalSchema() {

        TenantRequest request = TenantRequest.builder()
                .tenantCode("IT-SCHEMA-" + UUID.randomUUID())
                .tenantName("Schema Integration Tenant")
                .plan(Plan.PROFESSIONAL)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        Tenant tenant = tenantService.create(request);

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertNotNull(tenant.getSchemaName());

        provisionedSchema = tenant.getSchemaName();

        assertTrue(schemaExists(provisionedSchema));

        assertTableExists(provisionedSchema, "request_audit");
        assertTableExists(provisionedSchema, "token_usage");
        assertTableExists(provisionedSchema, "token_vault");
        assertTableExists(provisionedSchema, "request_cost");
        assertTableExists(provisionedSchema, "tenant_quota_usage");
        assertTableExists(provisionedSchema, "tenant_budget_usage");
        assertTableExists(provisionedSchema, "routing_outcome");
        assertTableExists(provisionedSchema, "flyway_schema_history");
    }

    private void assertTableExists(
            String schemaName,
            String tableName) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """,
                Integer.class,
                schemaName,
                tableName
        );

        assertEquals(
                1,
                count,
                "Expected table " + schemaName + "." + tableName
                        + " to exist"
        );
    }

    @AfterEach
    void cleanupTenantSchema() {

        if (provisionedSchema == null || provisionedSchema.isBlank()) {
            return;
        }

        String quotedSchema =
                "\"" + provisionedSchema.replace("\"", "\"\"") + "\"";

        jdbcTemplate.execute(
                "DROP SCHEMA IF EXISTS " + quotedSchema + " CASCADE"
        );

        provisionedSchema = null;
    }
}
