package com.ai.gateway.service;

import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.enums.PIIType;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.provisioning.TenantSchemaProvisioningService;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class TenantTokenVaultIsolationIntegrationTest {

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantSchemaRoutingService routingService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TokenVaultService tokenVaultService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private String tenantASchema;
    private String tenantBSchema;

    @AfterEach
    void cleanup() {
        dropSchema(tenantASchema);
        dropSchema(tenantBSchema);
        if (tenantAId != null) {
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantAId);
        }
        if (tenantBId != null) {
            jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", tenantBId);
        }
    }

    @Test
    void shouldStoreTokenVaultDataOnlyInAuthenticatedTenantSchema() {
        Tenant tenantA = createTenant("TOKEN-VAULT-ISO-A");
        Tenant tenantB = createTenant("TOKEN-VAULT-ISO-B");

        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();
        tenantASchema = provisioningService.provision(tenantA);
        tenantBSchema = provisioningService.provision(tenantB);

        UUID requestId = UUID.randomUUID();

        // Simulate the authenticated Tenant A request context.
        transactionTemplate.executeWithoutResult(status -> {
            com.ai.gateway.tenant.TenantContext.set(tenantAId);
            com.ai.gateway.tenant.TenantSchemaContext.set(tenantASchema);
            try {
                tokenVaultService.save(
                        requestId,
                        List.of(DetectedPII.builder()
                                .token("<PII_EMAIL_1>")
                                .originalValue("tenant-a@example.test")
                                .piiType(PIIType.EMAIL)
                                .build()));
            } finally {
                com.ai.gateway.tenant.TenantSchemaContext.clear();
                com.ai.gateway.tenant.TenantContext.clear();
            }
        });

        assertEquals(1, count(tenantASchema, requestId));
        assertEquals(0, count(tenantBSchema, requestId));
        assertEquals(0, count("public", requestId));
    }

    private Tenant createTenant(String code) {
        UUID id = UUID.randomUUID();
        String schema = "tenant_" + id.toString().replace("-", "").toLowerCase();

        jdbcTemplate.update("""
                INSERT INTO tenants (
                    id, tenant_code, tenant_name, schema_name,
                    status, type, plan, default_provider, default_model,
                    created_at, provisioning_attempts
                ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, CURRENT_TIMESTAMP, 0)
                """,
                id,
                code + "-" + id,
                code,
                schema,
                TenantType.STANDARD.name(),
                Plan.PROFESSIONAL.name(),
                Provider.GEMINI.name(),
                "gemini-3.6-flash");

        return tenantRepository.findById(id).orElseThrow();
    }

    private int count(String schema, UUID requestId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"" + schema + "\".token_vault WHERE request_uuid = ?",
                Integer.class,
                requestId);
    }

    private void dropSchema(String schema) {
        if (schema == null) return;
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }
}
