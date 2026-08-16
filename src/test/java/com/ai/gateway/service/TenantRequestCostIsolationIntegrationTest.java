package com.ai.gateway.service;

import com.ai.gateway.cost.entity.RequestCost;
import com.ai.gateway.cost.repository.RequestCostRepository;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provisioning.TenantSchemaProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import com.ai.gateway.entitlement.enums.Plan;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("test")
@SpringBootTest
class TenantRequestCostIsolationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RequestCostRepository requestCostRepository;

    @Autowired
    private TenantSchemaProvisioningService tenantSchemaProvisioningService;

    @Autowired
    private TenantSchemaRoutingService tenantSchemaRoutingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantAId;
    private UUID tenantBId;

    private String tenantASchema;
    private String tenantBSchema;

    @AfterEach
    void cleanup() {

        entityManager.clear();

        if (tenantASchema != null) {
            dropSchema(tenantASchema);
        }

        if (tenantBSchema != null) {
            dropSchema(tenantBSchema);
        }

        if (tenantAId != null) {
            jdbcTemplate.update(
                    "DELETE FROM tenants WHERE id = ?",
                    tenantAId);
        }

        if (tenantBId != null) {
            jdbcTemplate.update(
                    "DELETE FROM tenants WHERE id = ?",
                    tenantBId);
        }
    }

    @Test
    void shouldIsolateRequestCostBetweenTenants() {

        Tenant tenantA = createTenant("COST-ISO-A");
        Tenant tenantB = createTenant("COST-ISO-B");

        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        tenantASchema =
                tenantSchemaProvisioningService.provision(tenantA);

        tenantBSchema =
                tenantSchemaProvisioningService.provision(tenantB);

        /*
         * Persist one REQUEST_COST in Tenant A.
         */
        UUID requestId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {

            tenantSchemaRoutingService.useTenantSchema(
                    tenantAId);

            RequestCost requestCost =
                    RequestCost.builder()
                           // .id(UUID.randomUUID())
                            .requestId(requestId)
                            .tenantId(tenantAId)
                            .provider(Provider.OPENAI)
                            .model("gpt-test")
                            .inputTokens(100)
                            .outputTokens(50)
                            .totalTokens(150)
                            .inputCost(new BigDecimal("0.01"))
                            .outputCost(new BigDecimal("0.02"))
                            .totalCost(new BigDecimal("0.03"))
                            .createdAt(LocalDateTime.now())
                            .build();

            requestCostRepository.saveAndFlush(
                    requestCost);
        });

        /*
         * Verify physical Tenant A schema.
         */
        Integer tenantACount =
                countRequestCosts(
                        tenantASchema,
                        tenantAId);

        assertEquals(
                1,
                tenantACount,
                "Tenant A must contain its REQUEST_COST");

        /*
         * Verify physical Tenant B schema.
         */
        Integer tenantBCount =
                countRequestCosts(
                        tenantBSchema,
                        tenantAId);

        assertEquals(
                0,
                tenantBCount,
                "Tenant B must not contain Tenant A REQUEST_COST");

        /*
         * Verify repository routing through Tenant A.
         */
        Long repositoryTenantACount =
                transactionTemplate.execute(status -> {

                    tenantSchemaRoutingService.useTenantSchema(
                            tenantAId);

                    return requestCostRepository.count();
                });

        assertEquals(
                1L,
                repositoryTenantACount);

        /*
         * Clear persistence context before changing schemas.
         */
        entityManager.clear();

        /*
         * Verify repository routing through Tenant B.
         */
        Long repositoryTenantBCount =
                transactionTemplate.execute(status -> {

                    tenantSchemaRoutingService.useTenantSchema(
                            tenantBId);

                    return requestCostRepository.count();
                });

        assertEquals(
                0L,
                repositoryTenantBCount,
                "Tenant B repository must not see Tenant A data");
    }

    private Tenant createTenant(String code) {

        UUID id = UUID.randomUUID();

        String schema =
                "tenant_"
                        + id.toString()
                        .replace("-", "")
                        .toLowerCase();

        jdbcTemplate.update(
                """
                INSERT INTO tenants (
                    id,
                    tenant_code,
                    tenant_name,
                    schema_name,
                    plan
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                id,
                code + "-" + id,
                code,
                schema,
                "PROFESSIONAL");

        return tenantRepository.findById(id)
                .orElseThrow();
    }

    private Integer countRequestCosts(
            String schema,
            UUID tenantId) {

        String quotedSchema =
                quoteIdentifier(schema);

        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM %s.request_cost
                WHERE tenant_id = ?
                """.formatted(quotedSchema),
                Integer.class,
                tenantId);
    }

    private void dropSchema(String schema) {

        jdbcTemplate.execute(
                "DROP SCHEMA IF EXISTS "
                        + quoteIdentifier(schema)
                        + " CASCADE");
    }

    private String quoteIdentifier(String identifier) {

        if (!identifier.matches(
                "[a-zA-Z_][a-zA-Z0-9_]*")) {

            throw new IllegalArgumentException(
                    "Invalid SQL identifier: "
                            + identifier);
        }

        return "\""
                + identifier.replace("\"", "\"\"")
                + "\"";
    }
}