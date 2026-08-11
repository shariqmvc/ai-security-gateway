package com.ai.gateway.quota.service.impl;

import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.quota.TenantQuotaUsage;
import com.ai.gateway.quota.TenantQuotaUsageRepository;
import com.ai.gateway.quota.dto.QuotaUsageDto;
import com.ai.gateway.quota.dto.TenantQuotaUsageResponse;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {
    private final TenantQuotaUsageRepository usageRepository;

    private final EntitlementService entitlementService;

    @Override
    @Transactional
    public void consumeRequest(
            UUID tenantId) {

        TenantEntitlementResponse entitlement =
                entitlementService.get(tenantId);

        Long limit =
                entitlement.getRequestsPerDay();

        LocalDate today =
                LocalDate.now();

        ensureUsageRow(
                tenantId,
                QuotaPeriodType.DAILY,
                today);

        int updated =
                usageRepository.consumeRequest(
                        tenantId,
                        QuotaPeriodType.DAILY,
                        today,
                        limit);

        if (updated == 0) {

            throw new QuotaExceededException(
                    "Daily request quota exceeded.");
        }
    }

    @Override
    @Transactional
    public void consumeTokens(
            UUID tenantId,
            long tokens) {

        if (tokens <= 0) {
            return;
        }

        TenantEntitlementResponse entitlement =
                entitlementService.get(tenantId);

        Long limit =
                entitlement.getMonthlyTokenQuota();

        LocalDate monthStart =
                YearMonth.now()
                        .atDay(1);

        ensureUsageRow(
                tenantId,
                QuotaPeriodType.MONTHLY,
                monthStart);

        int updated =
                usageRepository.consumeTokens(
                        tenantId,
                        QuotaPeriodType.MONTHLY,
                        monthStart,
                        tokens,
                        limit);

        if (updated == 0) {

            throw new QuotaExceededException(
                    "Monthly token quota exceeded.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TenantQuotaUsageResponse getUsage(
            UUID tenantId) {

        TenantEntitlementResponse entitlement =
                entitlementService.get(tenantId);

        LocalDate today =
                LocalDate.now();

        LocalDate monthStart =
                YearMonth.now().atDay(1);

        TenantQuotaUsage dailyUsage =
                usageRepository
                        .findByTenantIdAndPeriodTypeAndPeriodStart(
                                tenantId,
                                QuotaPeriodType.DAILY,
                                today)
                        .orElse(null);

        TenantQuotaUsage monthlyUsage =
                usageRepository
                        .findByTenantIdAndPeriodTypeAndPeriodStart(
                                tenantId,
                                QuotaPeriodType.MONTHLY,
                                monthStart)
                        .orElse(null);

        long dailyUsed =
                dailyUsage != null
                        ? dailyUsage.getRequestCount()
                        : 0L;

        long monthlyTokensUsed =
                monthlyUsage != null
                        ? monthlyUsage.getTokenCount()
                        : 0L;

        long dailyLimit =
                entitlement.getRequestsPerDay();

        long monthlyTokenLimit =
                entitlement.getMonthlyTokenQuota();

        return TenantQuotaUsageResponse.builder()

                .tenantId(tenantId)

                .daily(
                        QuotaUsageDto.builder()
                                .used(dailyUsed)
                                .limit(dailyLimit)
                                .remaining(
                                        Math.max(
                                                0L,
                                                dailyLimit - dailyUsed))
                                .build())

                .monthlyTokens(
                        QuotaUsageDto.builder()
                                .used(monthlyTokensUsed)
                                .limit(monthlyTokenLimit)
                                .remaining(
                                        Math.max(
                                                0L,
                                                monthlyTokenLimit
                                                        - monthlyTokensUsed))
                                .build())

                .build();
    }

    private void ensureUsageRow(
            UUID tenantId,
            QuotaPeriodType periodType,
            LocalDate periodStart) {

        usageRepository.createIfAbsent(
                tenantId,
                periodType.name(),
                periodStart);
    }
}
