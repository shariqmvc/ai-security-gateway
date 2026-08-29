package com.ai.gateway.service;

import com.ai.gateway.budget.entity.TenantBudgetUsage;
import com.ai.gateway.budget.repository.TenantBudgetUsageRepository;
import com.ai.gateway.business.cost.RequestCost;
import com.ai.gateway.business.cost.RequestCostRepository;
import com.ai.gateway.entity.RequestAudit;
import com.ai.gateway.entity.TokenUsage;
import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.quota.TenantQuotaUsage;
import com.ai.gateway.quota.TenantQuotaUsageRepository;
import com.ai.gateway.repository.RequestAuditRepository;
import com.ai.gateway.repository.TokenUsageRepository;
import com.ai.gateway.repository.TokenVaultRepository;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.business.routing.health.entity.RoutingOutcome;
import com.ai.gateway.business.routing.health.repository.RoutingOutcomeRepository;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the physical tenant-schema isolation boundary for every tenant
 * operational table provisioned by V1__tenant_operational_schema.sql.
 *
 * A sentinel row is written only to Tenant A's schema. Tenant B must not see
 * any of Tenant A's rows, even when both schemas contain the same table names.
 * The repository checks additionally prove that JPA queries are executed
 * against the schema selected for the current transaction.
 */
@ActiveProfiles("test")
@SpringBootTest
class TenantOperationalSchemaIsolationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantSchemaRoutingService tenantSchemaRoutingService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RequestAuditRepository requestAuditRepository;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired
    private TokenVaultRepository tokenVaultRepository;

    @Autowired
    private RequestCostRepository requestCostRepository;

    @Autowired
    private TenantQuotaUsageRepository tenantQuotaUsageRepository;

    @Autowired
    private TenantBudgetUsageRepository tenantBudgetUsageRepository;

    @Autowired
    private RoutingOutcomeRepository routingOutcomeRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private String tenantASchema;
    private String tenantBSchema;

    @AfterEach
    void cleanup() {
        entityManager.clear();

        dropSchema(tenantASchema);
        dropSchema(tenantBSchema);

        deleteTenant(tenantAId);
        deleteTenant(tenantBId);
    }

    @Test
    void shouldIsolateAllTenantOperationalTables() {

        Tenant tenantA = createTenant("OPERATIONAL-ISO-A");
        Tenant tenantB = createTenant("OPERATIONAL-ISO-B");

        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();
        tenantASchema = tenantA.getSchemaName();
        tenantBSchema = tenantB.getSchemaName();

        UUID requestUuid = UUID.randomUUID();
        UUID tokenUsageRequestId = UUID.randomUUID();
        UUID routingRequestId = UUID.randomUUID();
        LocalDate periodStart = LocalDate.now().withDayOfMonth(1);

        insertTenantASentinels(
                tenantASchema,
                tenantAId,
                requestUuid,
                tokenUsageRequestId,
                routingRequestId,
                periodStart);

        // Physical schema boundary: Tenant B has none of Tenant A's rows.
        assertEquals(1, countByTenant(tenantASchema, "request_cost", tenantAId));
        assertEquals(0, countByTenant(tenantBSchema, "request_cost", tenantAId));

        assertEquals(1, countByTenant(tenantASchema, "tenant_quota_usage", tenantAId));
        assertEquals(0, countByTenant(tenantBSchema, "tenant_quota_usage", tenantAId));

        assertEquals(1, countByTenant(tenantASchema, "tenant_budget_usage", tenantAId));
        assertEquals(0, countByTenant(tenantBSchema, "tenant_budget_usage", tenantAId));

        assertEquals(1, countByRequest(tenantASchema, "request_audit", "request_uuid", requestUuid));
        assertEquals(0, countByRequest(tenantBSchema, "request_audit", "request_uuid", requestUuid));

        assertEquals(1, countByRequest(tenantASchema, "token_vault", "request_uuid", requestUuid));
        assertEquals(0, countByRequest(tenantBSchema, "token_vault", "request_uuid", requestUuid));

        assertEquals(1, countByRequest(tenantASchema, "token_usage", "request_id", tokenUsageRequestId));
        assertEquals(0, countByRequest(tenantBSchema, "token_usage", "request_id", tokenUsageRequestId));

        assertEquals(1, countByRequest(tenantASchema, "routing_outcome", "request_id", routingRequestId));
        assertEquals(0, countByRequest(tenantBSchema, "routing_outcome", "request_id", routingRequestId));

        // JPA schema routing boundary: the same repository must see A's data
        // in A's transaction and zero rows in B's transaction.
        assertRepositoryCounts(tenantAId, 1L);
        assertRepositoryCounts(tenantBId, 0L);
    }

    private void insertTenantASentinels(
            String schema,
            UUID tenantId,
            UUID requestUuid,
            UUID tokenUsageRequestId,
            UUID routingRequestId,
            LocalDate periodStart) {

        String s = quoteIdentifier(schema);

        jdbcTemplate.update(
                """
                INSERT INTO %s.request_audit (
                    request_uuid,
                    masked_prompt,
                    masked_response,
                    latency_ms,
                    model_name,
                    provider,
                    status,
                    created_at
                )
                VALUES (
                    ?,
                    lo_from_bytea(0, convert_to('TENANT_A_PROMPT', 'UTF8')),
                    NULL,
                    10,
                    'gpt-test',
                    'OPENAI',
                    'SUCCESS',
                    CURRENT_TIMESTAMP
                )
                """.formatted(s),
                requestUuid);

        jdbcTemplate.update(
                """
                INSERT INTO %s.token_vault (
                    request_uuid,
                    token,
                    encrypted_value,
                    pii_type,
                    created_at
                )
                VALUES (
                    ?,
                    'tenant-a-token',
                    lo_from_bytea(0, convert_to('TENANT_A_SECRET', 'UTF8')),
                    'EMAIL',
                    CURRENT_TIMESTAMP
                )
                """.formatted(s),
                requestUuid);

        jdbcTemplate.update(
                """
                INSERT INTO %s.token_usage (
                    id,
                    request_id,
                    provider,
                    model,
                    input_tokens,
                    output_tokens,
                    total_tokens,
                    reasoning_tokens,
                    created_at
                )
                VALUES (?, ?, 'OPENAI', 'gpt-test', 10, 20, 30, 0, CURRENT_TIMESTAMP)
                """.formatted(s),
                UUID.randomUUID(),
                tokenUsageRequestId);

        jdbcTemplate.update(
                """
                INSERT INTO %s.request_cost (
                    id,
                    request_id,
                    tenant_id,
                    provider,
                    model,
                    input_tokens,
                    output_tokens,
                    total_tokens,
                    input_cost,
                    output_cost,
                    total_cost,
                    reasoning_tokens,
                    created_at
                )
                VALUES (?, ?, ?, 'OPENAI', 'gpt-test', 10, 20, 30,
                        0.01, 0.02, 0.03, 0, CURRENT_TIMESTAMP)
                """.formatted(s),
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId);

        jdbcTemplate.update(
                """
                INSERT INTO %s.tenant_quota_usage (
                    id,
                    tenant_id,
                    period_type,
                    period_start,
                    request_count,
                    token_count,
                    version
                )
                VALUES (?, ?, 'MONTHLY', ?, 1, 30, 0)
                """.formatted(s),
                UUID.randomUUID(),
                tenantId,
                periodStart);

        jdbcTemplate.update(
                """
                INSERT INTO %s.tenant_budget_usage (
                    id,
                    tenant_id,
                    period_start,
                    amount_used,
                    version
                )
                VALUES (?, ?, ?, 0.03, 0)
                """.formatted(s),
                UUID.randomUUID(),
                tenantId,
                periodStart);

        jdbcTemplate.update(
                """
                INSERT INTO %s.routing_outcome (
                    id,
                    request_id,
                    tenant_id,
                    provider,
                    model,
                    routing_strategy,
                    selected_score,
                    selected_rank,
                    candidate_count,
                    selection_reason,
                    routing_priority,
                    extensive_research,
                    execution_role,
                    success,
                    failure_category,
                    latency_ms,
                    created_at
                )
                VALUES (?, ?, ?, 'OPENAI', 'gpt-test', 'DEFAULT',
                        0.90, 1, 1, 'TENANT_A_TEST', 'BALANCED', FALSE,
                        'PRIMARY', TRUE, NULL, 10, CURRENT_TIMESTAMP)
                """.formatted(s),
                UUID.randomUUID(),
                routingRequestId,
                tenantId);
    }

    private void assertRepositoryCounts(UUID tenantId, long expected) {
        entityManager.clear();

        Long count = transactionTemplate.execute(status -> {
            tenantSchemaRoutingService.useTenantSchema(tenantId);

            long total = 0;
            total += requestAuditRepository.count();
            total += tokenUsageRepository.count();
            total += tokenVaultRepository.count();
            total += requestCostRepository.count();
            total += tenantQuotaUsageRepository.count();
            total += tenantBudgetUsageRepository.count();
            total += routingOutcomeRepository.count();
            return total;
        });

        assertEquals(
                expected * 7,
                count,
                "All tenant operational repositories must follow the routed schema");
    }

    private int countByTenant(
            String schema,
            String table,
            UUID tenantId) {

        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.%s WHERE tenant_id = ?"
                        .formatted(quoteIdentifier(schema), table),
                Integer.class,
                tenantId);
    }

    private int countByRequest(
            String schema,
            String table,
            String column,
            UUID requestId) {

        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s.%s WHERE %s = ?"
                        .formatted(
                                quoteIdentifier(schema),
                                table,
                                column),
                Integer.class,
                requestId);
    }

    private Tenant createTenant(String prefix) {
        String code = prefix + "-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();

        return tenantService.create(
                TenantRequest.builder()
                        .tenantCode(code)
                        .tenantName(code)
                        .plan(Plan.PROFESSIONAL)
                        .type(TenantType.STANDARD)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-3.6-flash")
                        .build());
    }

    private void deleteTenant(UUID tenantId) {
        if (tenantId == null) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM API_KEYS WHERE tenant_id = ?",
                tenantId);

        jdbcTemplate.update(
                """
                DELETE FROM tenant_entitlement_features
                WHERE entitlement_id IN (
                    SELECT id
                    FROM tenant_entitlements
                    WHERE tenant_id = ?
                )
                """,
                tenantId);

        jdbcTemplate.update(
                "DELETE FROM tenant_entitlements WHERE tenant_id = ?",
                tenantId);

        jdbcTemplate.update(
                "DELETE FROM TENANTS WHERE id = ?",
                tenantId);
    }

    private void dropSchema(String schema) {
        if (schema == null) {
            return;
        }

        jdbcTemplate.execute(
                "DROP SCHEMA IF EXISTS "
                        + quoteIdentifier(schema)
                        + " CASCADE");
    }

    private String quoteIdentifier(String identifier) {
        if (!identifier.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid SQL identifier: " + identifier);
        }

        return "\"" + identifier + "\"";
    }
}
