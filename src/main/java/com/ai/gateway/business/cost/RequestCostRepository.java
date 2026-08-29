package com.ai.gateway.business.cost;

import com.ai.gateway.core.cost.dto.CostSummary;
import com.ai.gateway.business.cost.RequestCost;
import com.ai.gateway.core.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RequestCostRepository
        extends JpaRepository<RequestCost, UUID> {


    @Query("""
        SELECT new com.ai.gateway.core.cost.dto.CostSummary(
            COALESCE(SUM(r.totalCost),0),
            COALESCE(SUM(r.inputCost),0),
            COALESCE(SUM(r.outputCost),0),
            COUNT(r),
            COALESCE(SUM(r.inputTokens),0),
            COALESCE(SUM(r.outputTokens),0)
        )
        FROM RequestCost r
    """)
    CostSummary getOverallSummary();

    @Query("""
SELECT new com.ai.gateway.core.cost.dto.CostSummary(
    COALESCE(SUM(r.totalCost),0),
    COALESCE(SUM(r.inputCost),0),
    COALESCE(SUM(r.outputCost),0),
    COUNT(r),
    COALESCE(SUM(r.inputTokens),0L),
    COALESCE(SUM(r.outputTokens),0L)
)
FROM RequestCost r
WHERE r.tenantId = :tenantId
""")
    CostSummary getTenantSummary(UUID tenantId);

    @Query("""
SELECT new com.ai.gateway.core.cost.dto.CostSummary(
    COALESCE(SUM(r.totalCost),0),
    COALESCE(SUM(r.inputCost),0),
    COALESCE(SUM(r.outputCost),0),
    COUNT(r),
    COALESCE(SUM(r.inputTokens),0L),
    COALESCE(SUM(r.outputTokens),0L)
)
FROM RequestCost r
WHERE r.provider = :provider
""")
    CostSummary getProviderSummary(Provider provider);

    @Query("""
SELECT new com.ai.gateway.core.cost.dto.CostSummary(
    COALESCE(SUM(r.totalCost),0),
    COALESCE(SUM(r.inputCost),0),
    COALESCE(SUM(r.outputCost),0),
    COUNT(r),
    COALESCE(SUM(r.inputTokens),0L),
    COALESCE(SUM(r.outputTokens),0L)
)
FROM RequestCost r
WHERE r.model = :model
""")
    CostSummary getModelSummary(String model);

    @Query("""
SELECT new com.ai.gateway.core.cost.dto.CostSummary(
    COALESCE(SUM(r.totalCost),0),
    COALESCE(SUM(r.inputCost),0),
    COALESCE(SUM(r.outputCost),0),
    COUNT(r),
    COALESCE(SUM(r.inputTokens),0L),
    COALESCE(SUM(r.outputTokens),0L)
)
FROM RequestCost r
WHERE r.createdAt >= :start
AND r.createdAt < :end
""")
    CostSummary getSummaryBetween(
            LocalDateTime start,
            LocalDateTime end);

}
