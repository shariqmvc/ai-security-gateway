package com.ai.gateway.quota;

import com.ai.gateway.enums.QuotaPeriodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TenantQuotaUsageRepository
        extends JpaRepository<TenantQuotaUsage, UUID> {

    Optional<TenantQuotaUsage>
    findByTenantIdAndPeriodTypeAndPeriodStart(
            UUID tenantId,
            QuotaPeriodType periodType,
            LocalDate periodStart);

    @Modifying
    @Query("""
            UPDATE TenantQuotaUsage q
               SET q.requestCount = q.requestCount + 1
             WHERE q.tenantId = :tenantId
               AND q.periodType = :periodType
               AND q.periodStart = :periodStart
               AND q.requestCount < :limit
            """)
    int consumeRequest(
            @Param("tenantId") UUID tenantId,
            @Param("periodType") QuotaPeriodType periodType,
            @Param("periodStart") LocalDate periodStart,
            @Param("limit") Long limit);

    @Modifying
    @Query("""
            UPDATE TenantQuotaUsage q
               SET q.tokenCount = q.tokenCount + :tokens
             WHERE q.tenantId = :tenantId
               AND q.periodType = :periodType
               AND q.periodStart = :periodStart
               AND q.tokenCount + :tokens <= :limit
            """)
    int consumeTokens(
            @Param("tenantId") UUID tenantId,
            @Param("periodType") QuotaPeriodType periodType,
            @Param("periodStart") LocalDate periodStart,
            @Param("tokens") Long tokens,
            @Param("limit") Long limit);

    @Modifying
    @Query(value = """
        INSERT INTO TENANT_QUOTA_USAGE
            (id, tenant_id, period_type, period_start,
             request_count, token_count, version)
        VALUES
            (gen_random_uuid(), :tenantId, :periodType, :periodStart,
             0, 0, 0)
        ON CONFLICT (tenant_id, period_type, period_start)
        DO NOTHING
        """,
            nativeQuery = true)
    int createIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("periodType") String periodType,
            @Param("periodStart") LocalDate periodStart);


}
