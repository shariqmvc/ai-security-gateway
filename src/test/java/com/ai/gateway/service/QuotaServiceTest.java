package com.ai.gateway.service;

import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.QuotaPeriodType;
import com.ai.gateway.quota.TenantQuotaUsageRepository;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.quota.service.impl.QuotaServiceImpl;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotaServiceTest {

    @Mock
    private TenantQuotaUsageRepository usageRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private TenantSchemaRoutingService tenantSchemaRoutingService;

    @InjectMocks
    private QuotaServiceImpl quotaService;

    @Test
    void shouldConsumeDailyRequest() {

        UUID tenantId =
                UUID.randomUUID();

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .requestsPerDay(2L)
                        .monthlyTokenQuota(1000L)
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(
                usageRepository.consumeRequest(
                        eq(tenantId),
                        eq(QuotaPeriodType.DAILY),
                        any(LocalDate.class),
                        eq(2L)))
                .thenReturn(1);

        assertDoesNotThrow(
                () -> quotaService.consumeRequest(
                        tenantId));

        verify(
                usageRepository)
                .createIfAbsent(
                        eq(tenantId),
                        eq(QuotaPeriodType.DAILY.name()),
                        any(LocalDate.class));

        verify(tenantSchemaRoutingService)
                .useTenantSchema(tenantId);

        verify(
                usageRepository)
                .consumeRequest(
                        eq(tenantId),
                        eq(QuotaPeriodType.DAILY),
                        any(LocalDate.class),
                        eq(2L));
    }

    @Test
    void shouldRejectWhenDailyQuotaExceeded() {

        UUID tenantId =
                UUID.randomUUID();

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .requestsPerDay(2L)
                        .monthlyTokenQuota(1000L)
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(
                usageRepository.consumeRequest(
                        eq(tenantId),
                        eq(QuotaPeriodType.DAILY),
                        any(LocalDate.class),
                        eq(2L)))
                .thenReturn(0);

        assertThrows(
                QuotaExceededException.class,
                () -> quotaService.consumeRequest(
                        tenantId));
    }

    @Test
    void shouldConsumeMonthlyTokens() {

        UUID tenantId =
                UUID.randomUUID();

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .requestsPerDay(100L)
                        .monthlyTokenQuota(1000L)
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(
                usageRepository.consumeTokens(
                        eq(tenantId),
                        eq(QuotaPeriodType.MONTHLY),
                        eq(
                                YearMonth.now()
                                        .atDay(1)),
                        eq(500L),
                        eq(1000L)))
                .thenReturn(1);

        assertDoesNotThrow(
                () -> quotaService.consumeTokens(
                        tenantId,
                        500L));

        verify(
                usageRepository)
                .createIfAbsent(
                        eq(tenantId),
                        eq(QuotaPeriodType.MONTHLY.name()),
                        eq(
                                YearMonth.now()
                                        .atDay(1)));

        verify(tenantSchemaRoutingService)
                .useTenantSchema(tenantId);

        verify(
                usageRepository)
                .consumeTokens(
                        eq(tenantId),
                        eq(QuotaPeriodType.MONTHLY),
                        eq(
                                YearMonth.now()
                                        .atDay(1)),
                        eq(500L),
                        eq(1000L));
    }

    @Test
    void shouldRejectWhenMonthlyTokenQuotaExceeded() {

        UUID tenantId =
                UUID.randomUUID();

        TenantEntitlementResponse entitlement =
                TenantEntitlementResponse.builder()
                        .requestsPerDay(100L)
                        .monthlyTokenQuota(1000L)
                        .build();

        when(entitlementService.get(tenantId))
                .thenReturn(entitlement);

        when(
                usageRepository.consumeTokens(
                        eq(tenantId),
                        eq(QuotaPeriodType.MONTHLY),
                        eq(
                                YearMonth.now()
                                        .atDay(1)),
                        eq(1001L),
                        eq(1000L)))
                .thenReturn(0);

        assertThrows(
                QuotaExceededException.class,
                () -> quotaService.consumeTokens(
                        tenantId,
                        1001L));
    }

    @Test
    void shouldIgnoreZeroOrNegativeTokens() {

        UUID tenantId =
                UUID.randomUUID();

        assertDoesNotThrow(
                () -> quotaService.consumeTokens(
                        tenantId,
                        0));

        assertDoesNotThrow(
                () -> quotaService.consumeTokens(
                        tenantId,
                        -10));

        verifyNoInteractions(
                entitlementService,
                usageRepository);
    }
}
