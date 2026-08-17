package com.ai.gateway.budget.repository;

import com.ai.gateway.budget.entity.TenantBudgetUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TenantBudgetUsageRepository
        extends JpaRepository<
                TenantBudgetUsage,
                UUID> {

    Optional<TenantBudgetUsage>
    findByTenantIdAndPeriodStart(
            UUID tenantId,
            LocalDate periodStart);

    /** Atomic monthly budget consume: insert-or-update in one DB round trip. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO TENANT_BUDGET_USAGE
                (id, tenant_id, period_start, amount_used, version)
            SELECT gen_random_uuid(), :tenantId, :periodStart, :amount, 0
            WHERE :amount <= :budget
            ON CONFLICT (tenant_id, period_start)
            DO UPDATE SET
                amount_used = TENANT_BUDGET_USAGE.amount_used + EXCLUDED.amount_used
            WHERE TENANT_BUDGET_USAGE.amount_used + EXCLUDED.amount_used <= :budget
            """,
            nativeQuery = true)
    int consume(
            @Param("tenantId")
            UUID tenantId,

            @Param("periodStart")
            LocalDate periodStart,

            @Param("amount")
            BigDecimal amount,

            @Param("budget")
            BigDecimal budget);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO TENANT_BUDGET_USAGE
            (id, tenant_id, period_start,
             amount_used, version)
        VALUES
            (gen_random_uuid(),
             :tenantId,
             :periodStart,
             0,
             0)
        ON CONFLICT (tenant_id, period_start)
        DO NOTHING
        """,
            nativeQuery = true)
    int createIfAbsent(
            @Param("tenantId")
            UUID tenantId,

            @Param("periodStart")
            LocalDate periodStart);
}