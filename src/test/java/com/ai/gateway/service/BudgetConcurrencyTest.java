package com.ai.gateway.service;

import com.ai.gateway.budget.BudgetExceededException;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.entitlement.service.EntitlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BudgetConcurrencyTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntitlementService entitlementService;

    private UUID tenantId;

    @AfterEach
    void cleanup() {

        if (tenantId == null) {
            return;
        }

        jdbcTemplate.update(
                """
                DELETE FROM tenant_budget_usage
                WHERE tenant_id = ?
                """,
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
                """
                DELETE FROM tenant_entitlements
                WHERE tenant_id = ?
                """,
                tenantId);

        jdbcTemplate.update(
                """
                DELETE FROM tenants
                WHERE id = ?
                """,
                tenantId);
    }

    @Test
    void shouldPreventConcurrentOverspending()
            throws Exception {

        tenantId = createTenantWithBudget(
                new BigDecimal("0.20"));

        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        Future<Boolean> first =
                executor.submit(() -> {

                    await(startLatch);

                    try {
                        budgetService.consume(
                                tenantId,
                                new BigDecimal("0.15"));

                        return true;

                    } catch (BudgetExceededException e) {
                        return false;
                    }
                });

        Future<Boolean> second =
                executor.submit(() -> {

                    await(startLatch);

                    try {
                        budgetService.consume(
                                tenantId,
                                new BigDecimal("0.15"));


                        return true;

                    } catch (BudgetExceededException e) {
                        return false;
                    }
                });

        startLatch.countDown();

        boolean firstSucceeded = first.get();
        boolean secondSucceeded = second.get();

        executor.shutdown();

        assertTrue(
                firstSucceeded ^ secondSucceeded,
                "Exactly one request must succeed");

        BigDecimal used =
                jdbcTemplate.queryForObject(
                        """
                        SELECT amount_used
                        FROM tenant_budget_usage
                        WHERE tenant_id = ?
                        """,
                        BigDecimal.class,
                        tenantId);

        assertEquals(
                new BigDecimal("0.15"),
                used.setScale(2));
    }

    private UUID createTenantWithBudget(
            BigDecimal budget) {

        UUID id = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO tenants (
                    id,
                    tenant_code,
                    tenant_name,
                    plan
                )
                VALUES (?, ?, ?, ?)
                """,
                id,
                "BUDGET-CONCURRENT-" + id,
                "Budget Concurrency Test",
                "PROFESSIONAL");

        UUID entitlementId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO tenant_entitlements (
                    id,
                    tenant_id,
                    requests_per_minute,
                    requests_per_day,
                    monthly_token_quota,
                    monthly_budget,
                    enabled,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, 100, 10000,
                    10000000, ?, true,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                entitlementId,
                id,
                budget);

        tenantId = id;

        return id;
    }

    private void await(
            CountDownLatch latch) {

        try {
            latch.await();
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Thread interrupted",
                    e);
        }
    }
}
