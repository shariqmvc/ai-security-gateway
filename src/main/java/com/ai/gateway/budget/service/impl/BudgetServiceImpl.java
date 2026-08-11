package com.ai.gateway.budget.service.impl;

import com.ai.gateway.budget.BudgetExceededException;
import com.ai.gateway.budget.dto.BudgetUsageResponse;
import com.ai.gateway.budget.repository.TenantBudgetUsageRepository;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetServiceImpl
        implements BudgetService {

    private final TenantBudgetUsageRepository repository;

    private final EntitlementService entitlementService;

    @Override
    @Transactional
    public void consume(
            UUID tenantId,
            BigDecimal amount) {

        if (amount == null
                || amount.compareTo(
                BigDecimal.ZERO) <= 0) {

            return;
        }

        TenantEntitlementResponse entitlement =
                entitlementService.get(tenantId);

        BigDecimal budget =
                entitlement.getMonthlyBudget();

        if (budget == null
                || budget.compareTo(
                BigDecimal.ZERO) <= 0) {

            return;
        }

        LocalDate monthStart =
                YearMonth.now().atDay(1);

        repository.createIfAbsent(
                tenantId,
                monthStart);

        int updated =
                repository.consume(
                        tenantId,
                        monthStart,
                        amount,
                        budget);

        if (updated == 0) {

            throw new BudgetExceededException(
                    "Monthly budget exceeded.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetUsageResponse getUsage(
            UUID tenantId) {

        TenantEntitlementResponse entitlement =
                entitlementService.get(tenantId);

        BigDecimal budget =
                entitlement.getMonthlyBudget();

        LocalDate monthStart =
                YearMonth.now().atDay(1);

        BigDecimal used =
                repository
                        .findByTenantIdAndPeriodStart(
                                tenantId,
                                monthStart)
                        .map(
                                usage ->
                                        usage.getAmountUsed())
                        .orElse(
                                BigDecimal.ZERO);

        BigDecimal remaining =
                budget.subtract(used);

        if (remaining.compareTo(
                BigDecimal.ZERO) < 0) {

            remaining = BigDecimal.ZERO;
        }

        return BudgetUsageResponse.builder()
                .tenantId(tenantId)
                .used(used)
                .limit(budget)
                .remaining(remaining)
                .build();
    }
}