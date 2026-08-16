package com.ai.gateway.service;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.provisioning.TenantSchemaProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "gemini.api.key=test-gemini-key"
})
class TenantSchemaProvisioningIntegrationTest {

    @Autowired
    private TenantSchemaProvisioningService schemaProvisioningService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;
    private String schemaName;

    @AfterEach
    void cleanup() {
        if (schemaName != null) {
            jdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS \"" +
                            schemaName.replace("\"", "\"\"") +
                            "\" CASCADE");
        }

        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void shouldCreateTenantSchemaAndRunTenantMigrations() {

        Tenant tenant = Tenant.builder()
                .tenantCode("SCHEMA-INT-" + UUID.randomUUID())
                .tenantName("Schema Integration Tenant")
                .status(TenantStatus.PROVISIONING)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        tenant = tenantRepository.saveAndFlush(tenant);
        tenantId = tenant.getId();

        schemaName = schemaProvisioningService.provision(tenant);

        tenantRepository.saveAndFlush(tenant);

        assertNotNull(schemaName);
        assertTrue(schemaName.startsWith("tenant_"));

        assertEquals(schemaName, tenant.getSchemaName());

        assertTrue(schemaExists(schemaName));

        assertTenantTableExists(schemaName, "request_audit");
        assertTenantTableExists(schemaName, "token_usage");
        assertTenantTableExists(schemaName, "token_vault");
        assertTenantTableExists(schemaName, "request_cost");
        assertTenantTableExists(schemaName, "tenant_quota_usage");
        assertTenantTableExists(schemaName, "tenant_budget_usage");
        assertTenantTableExists(schemaName, "routing_outcome");
    }

    @Test
    void shouldBeIdempotentWhenSchemaProvisioningRunsTwice() {

        Tenant tenant = Tenant.builder()
                .tenantCode("SCHEMA-IDEMP-" + UUID.randomUUID())
                .tenantName("Schema Idempotency Tenant")
                .status(TenantStatus.PROVISIONING)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        tenant = tenantRepository.saveAndFlush(tenant);
        tenantId = tenant.getId();

        String firstSchema =
                schemaProvisioningService.provision(tenant);

        tenantRepository.saveAndFlush(tenant);

        String secondSchema =
                schemaProvisioningService.provision(tenant);

        schemaName = firstSchema;

        assertEquals(firstSchema, secondSchema);
        assertTrue(schemaExists(firstSchema));

        assertTenantTableExists(firstSchema, "request_audit");
        assertTenantTableExists(firstSchema, "token_usage");
        assertTenantTableExists(firstSchema, "routing_outcome");
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
}