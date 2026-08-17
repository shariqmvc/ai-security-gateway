package com.ai.gateway.service;

import com.ai.gateway.security.ApiKeyProvisioningResult;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies both sides of tenant isolation at the HTTP boundary:
 *
 * 1. A tenant can read its own operational cost data.
 * 2. A tenant cannot select another tenant by changing {tenantId}.
 * 3. Tenant-wide cost summaries are routed to the authenticated tenant
 *    schema rather than the public schema.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class TenantCostAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantAId;
    private UUID tenantBId;
    private String tenantASchema;
    private String tenantBSchema;

    private String tenantAKey;
    private String tenantBKey;

    @AfterEach
    void cleanup() {
        dropSchema(tenantASchema);
        dropSchema(tenantBSchema);

        deleteTenant(tenantAId);
        deleteTenant(tenantBId);
    }

    @Test
    void shouldAllowOwnTenantAndRejectCrossTenantCostAccess()
            throws Exception {

        Tenant tenantA = createTenant("HTTP-COST-ISO-A");
        Tenant tenantB = createTenant("HTTP-COST-ISO-B");

        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();
        tenantASchema = tenantA.getSchemaName();
        tenantBSchema = tenantB.getSchemaName();

        tenantAKey = rotateAndGetSecret(tenantA);
        tenantBKey = rotateAndGetSecret(tenantB);

        insertCost(
                tenantASchema,
                tenantAId,
                new BigDecimal("1.11"),
                10,
                100);

        insertCost(
                tenantBSchema,
                tenantBId,
                new BigDecimal("2.22"),
                20,
                200);

        // Tenant A can read Tenant A.
        mockMvc.perform(
                        get("/api/cost/tenant/{tenantId}", tenantAId)
                                .header("X-API-Key", tenantAKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.totalInputTokens").value(10))
                .andExpect(jsonPath("$.totalOutputTokens").value(100))
                .andExpect(jsonPath("$.totalCost").value(1.11));

        // Tenant B can read Tenant B.
        mockMvc.perform(
                        get("/api/cost/tenant/{tenantId}", tenantBId)
                                .header("X-API-Key", tenantBKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.totalInputTokens").value(20))
                .andExpect(jsonPath("$.totalOutputTokens").value(200))
                .andExpect(jsonPath("$.totalCost").value(2.22));

        // Tenant A must not be able to select Tenant B's schema/data.
        mockMvc.perform(
                        get("/api/cost/tenant/{tenantId}", tenantBId)
                                .header("X-API-Key", tenantAKey))
                .andExpect(status().isForbidden());

        // Tenant B must not be able to select Tenant A's schema/data.
        mockMvc.perform(
                        get("/api/cost/tenant/{tenantId}", tenantAId)
                                .header("X-API-Key", tenantBKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldScopeOverallCostSummaryToAuthenticatedTenant()
            throws Exception {

        Tenant tenantA = createTenant("HTTP-COST-SUMMARY-A");
        Tenant tenantB = createTenant("HTTP-COST-SUMMARY-B");

        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();
        tenantASchema = tenantA.getSchemaName();
        tenantBSchema = tenantB.getSchemaName();

        tenantAKey = rotateAndGetSecret(tenantA);
        tenantBKey = rotateAndGetSecret(tenantB);

        insertCost(
                tenantASchema,
                tenantAId,
                new BigDecimal("3.33"),
                30,
                300);

        insertCost(
                tenantBSchema,
                tenantBId,
                new BigDecimal("4.44"),
                40,
                400);

        // /summary must use the authenticated tenant's schema.
        mockMvc.perform(
                        get("/api/cost/summary")
                                .header("X-API-Key", tenantAKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.totalInputTokens").value(30))
                .andExpect(jsonPath("$.totalOutputTokens").value(300))
                .andExpect(jsonPath("$.totalCost").value(3.33));

        mockMvc.perform(
                        get("/api/cost/summary")
                                .header("X-API-Key", tenantBKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.totalInputTokens").value(40))
                .andExpect(jsonPath("$.totalOutputTokens").value(400))
                .andExpect(jsonPath("$.totalCost").value(4.44));
    }

    private Tenant createTenant(String prefix) {
        String code = prefix + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

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

    private String rotateAndGetSecret(Tenant tenant) {
        ApiKeyProvisioningResult result =
                apiKeyService.rotate(tenant, "tenant-isolation-test");

        if (result.apiKey() == null || result.apiKey().isBlank()) {
            throw new IllegalStateException(
                    "Test API key rotation did not return a secret.");
        }

        return result.apiKey();
    }

    private void insertCost(
            String schema,
            UUID tenantId,
            BigDecimal totalCost,
            int inputTokens,
            int outputTokens) {

        String quotedSchema = quoteIdentifier(schema);

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
                VALUES (
                    ?, ?, ?, 'GEMINI', 'gemini-3.6-flash',
                    ?, ?, ?, 0, ?, ?, 0, CURRENT_TIMESTAMP
                )
                """.formatted(quotedSchema),
                UUID.randomUUID(),
                UUID.randomUUID(),
                tenantId,
                inputTokens,
                outputTokens,
                inputTokens + outputTokens,
                totalCost,
                totalCost);
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
