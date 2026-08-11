package com.ai.gateway.service;

import com.ai.gateway.budget.BudgetExceededException;
import com.ai.gateway.budget.dto.BudgetUsageResponse;
import com.ai.gateway.budget.entity.TenantBudgetUsage;
import com.ai.gateway.budget.repository.TenantBudgetUsageRepository;
import com.ai.gateway.budget.service.impl.BudgetServiceImpl;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private TenantBudgetUsageRepository repository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private TenantBudgetUsage usage;

    @InjectMocks
    private BudgetServiceImpl budgetService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        tenantId = UUID.randomUUID();
    }

    @Test
    void shouldConsumeWithinBudget() {

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .monthlyBudget(
                                new BigDecimal("500.00"))
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(repository.consume(
                eq(tenantId),
                any(LocalDate.class),
                eq(new BigDecimal("100.00")),
                eq(new BigDecimal("500.00"))))
                .thenReturn(1);

        budgetService.consume(
                tenantId,
                new BigDecimal("100.00"));

        verify(repository)
                .createIfAbsent(
                        eq(tenantId),
                        eq(YearMonth.now().atDay(1)));

        verify(repository)
                .consume(
                        eq(tenantId),
                        eq(YearMonth.now().atDay(1)),
                        eq(new BigDecimal("100.00")),
                        eq(new BigDecimal("500.00")));
    }

    @Test
    void shouldRejectWhenBudgetExceeded() {

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .monthlyBudget(
                                new BigDecimal("500.00"))
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(repository.consume(
                eq(tenantId),
                any(LocalDate.class),
                eq(new BigDecimal("50.00")),
                eq(new BigDecimal("500.00"))))
                .thenReturn(0);

        BudgetExceededException exception =
                assertThrows(
                        BudgetExceededException.class,
                        () ->
                                budgetService.consume(
                                        tenantId,
                                        new BigDecimal("50.00")));

        assertEquals(
                "Monthly budget exceeded.",
                exception.getMessage());

        verify(repository)
                .createIfAbsent(
                        eq(tenantId),
                        eq(YearMonth.now().atDay(1)));
    }

    @Test
    void shouldIgnoreZeroAmount() {

        budgetService.consume(
                tenantId,
                BigDecimal.ZERO);

        verifyNoInteractions(
                entitlementService,
                repository);
    }

    @Test
    void shouldIgnoreNegativeAmount() {

        budgetService.consume(
                tenantId,
                new BigDecimal("-10.00"));

        verifyNoInteractions(
                entitlementService,
                repository);
    }

    @Test
    void shouldIgnoreWhenBudgetNotConfigured() {

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .monthlyBudget(null)
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        budgetService.consume(
                tenantId,
                new BigDecimal("100.00"));

        verify(entitlementService)
                .get(tenantId);

        verifyNoInteractions(repository);
    }

    @Test
    void shouldReturnCurrentUsage() {

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .monthlyBudget(
                                new BigDecimal("500.00"))
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(usage.getAmountUsed())
                .thenReturn(
                        new BigDecimal("125.50"));

        LocalDate monthStart =
                YearMonth.now().atDay(1);

        when(repository.findByTenantIdAndPeriodStart(
                tenantId,
                monthStart))
                .thenReturn(Optional.of(usage));

        BudgetUsageResponse result =
                budgetService.getUsage(
                        tenantId);

        assertEquals(
                tenantId,
                result.getTenantId());

        assertEquals(
                new BigDecimal("125.50"),
                result.getUsed());

        assertEquals(
                new BigDecimal("500.00"),
                result.getLimit());

        assertEquals(
                new BigDecimal("374.50"),
                result.getRemaining());
    }

    @Test
    void shouldReturnZeroUsageWhenNoUsageRecordExists() {

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .monthlyBudget(
                                new BigDecimal("500.00"))
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        LocalDate monthStart =
                YearMonth.now().atDay(1);

        when(repository.findByTenantIdAndPeriodStart(
                tenantId,
                monthStart))
                .thenReturn(Optional.empty());

        BudgetUsageResponse result =
                budgetService.getUsage(
                        tenantId);

        assertEquals(
                BigDecimal.ZERO,
                result.getUsed());

        assertEquals(
                new BigDecimal("500.00"),
                result.getLimit());

        assertEquals(
                new BigDecimal("500.00"),
                result.getRemaining());
    }
}
