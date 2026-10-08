package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.service.PreRequestCostEstimator;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalCreditExecutionServiceTest {

    @Mock
    private PersonalCreditService creditService;
    @Mock
    private PreRequestCostEstimator costEstimator;
    @Mock
    private PersonalBillingSettingsRepository billingSettingsRepository;
    @Mock
    private PersonalCreditLedgerRepository ledgerRepository;
    @Mock
    private PersonalCreditReservationRepository reservationRepository;

    private PersonalBillingProperties properties;
    private PersonalCreditExecutionService service;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        properties = new PersonalBillingProperties();
        properties.setReservationMultiplier(BigDecimal.ONE);
        properties.setReserveOutputTokens(4096);
        properties.setMinimumCreditCharge(new BigDecimal("0.01"));
        service = new PersonalCreditExecutionService(
                creditService,
                costEstimator,
                properties,
                billingSettingsRepository,
                ledgerRepository,
                reservationRepository);
        accountId = UUID.randomUUID();
    }

    @Test
    void reconciliationPersistsSettlementIntentBeforeCapture() {
        UUID reservationId = UUID.randomUUID();
        when(costEstimator.estimate(any())).thenReturn(PreRequestCostEstimate.builder()
                .totalEstimatedCost(new BigDecimal("0.25"))
                .build());
        var reservationContext = new PersonalCreditExecutionService.ReservationContext(
                reservationId, new BigDecimal("0.50"));
        AIRequest request = AIRequest.builder().provider(Provider.OPENAI).model("gpt-test").prompt("hello").build();

        service.reconcile(reservationContext, request, com.ai.gateway.core.contract.AIResponse.builder().build());

        InOrder order = inOrder(creditService);
        order.verify(creditService).markProviderInvocationSucceeded(
                reservationId, "OPENAI", "gpt-test", 0, 0);
        order.verify(creditService).prepareCapture(reservationId, new BigDecimal("0.25"));
        order.verify(creditService).capture(reservationId, new BigDecimal("0.25"));
        verify(creditService, never()).release(reservationId);
    }

    @Test
    void reconciliationLeavesReservationForRecoveryWhenCaptureFails() {
        UUID reservationId = UUID.randomUUID();
        when(costEstimator.estimate(any())).thenReturn(PreRequestCostEstimate.builder()
                .totalEstimatedCost(new BigDecimal("0.25"))
                .build());
        doThrow(new IllegalStateException("database temporarily unavailable"))
                .when(creditService).capture(reservationId, new BigDecimal("0.25"));
        var reservationContext = new PersonalCreditExecutionService.ReservationContext(
                reservationId, new BigDecimal("0.50"));
        AIRequest request = AIRequest.builder().provider(Provider.OPENAI).model("gpt-test").prompt("hello").build();

        assertThrows(IllegalStateException.class,
                () -> service.reconcile(reservationContext, request,
                        com.ai.gateway.core.contract.AIResponse.builder().build()));

        InOrder order = inOrder(creditService);
        order.verify(creditService).markProviderInvocationSucceeded(
                reservationId, "OPENAI", "gpt-test", 0, 0);
        order.verify(creditService).prepareCapture(reservationId, new BigDecimal("0.25"));
        verify(creditService, never()).release(reservationId);
    }

    @Test
    void reconciliationCapsActualCostAtReservationAndPersistsThatAmount() {
        UUID reservationId = UUID.randomUUID();
        when(costEstimator.estimate(any())).thenReturn(PreRequestCostEstimate.builder()
                .totalEstimatedCost(new BigDecimal("0.75"))
                .build());
        var reservationContext = new PersonalCreditExecutionService.ReservationContext(
                reservationId, new BigDecimal("0.50"));
        AIRequest request = AIRequest.builder().provider(Provider.OPENAI).model("gpt-test").prompt("hello").build();

        assertThrows(PersonalBillingModeException.class,
                () -> service.reconcile(reservationContext, request,
                        com.ai.gateway.core.contract.AIResponse.builder().build()));

        InOrder order = inOrder(creditService);
        order.verify(creditService).markProviderInvocationSucceeded(
                reservationId, "OPENAI", "gpt-test", 0, 0);
        order.verify(creditService).prepareCapture(reservationId, new BigDecimal("0.50"));
        order.verify(creditService).capture(reservationId, new BigDecimal("0.50"));
        verify(creditService, never()).release(reservationId);
    }

    @Test
    void recoveryRecomputesCostFromPersistedProviderUsageWhenAmountWasNotStored() {
        UUID reservationId = UUID.randomUUID();
        PersonalCreditReservation reservation = PersonalCreditReservation.builder()
                .id(reservationId)
                .reservedAmount(new BigDecimal("0.50"))
                .status(com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus.SETTLEMENT_PENDING)
                .settlementProvider("OPENAI")
                .settlementModel("gpt-test")
                .settlementInputTokens(100)
                .settlementOutputTokens(200)
                .build();
        when(costEstimator.estimate(any())).thenReturn(PreRequestCostEstimate.builder()
                .totalEstimatedCost(new BigDecimal("0.25"))
                .build());

        service.recoverPendingSettlement(reservation);

        verify(creditService).prepareCapture(reservationId, new BigDecimal("0.25"));
        verify(creditService).capture(reservationId, new BigDecimal("0.25"));
    }

    @Test
    void outstandingReservationsAreIncludedInMonthlySpendCapBeforeNewReservation() {
        when(costEstimator.estimate(any())).thenReturn(PreRequestCostEstimate.builder()
                .totalEstimatedCost(new BigDecimal("0.50"))
                .build());
        when(billingSettingsRepository.findByPersonalAccountId(accountId))
                .thenReturn(Optional.of(PersonalBillingSettings.builder()
                        .personalAccountId(accountId)
                        .monthlySpendCap(new BigDecimal("1.00"))
                        .updatedAt(LocalDateTime.now())
                        .build()));
        when(ledgerRepository.sumCapturedSince(eq(accountId), any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);
        when(reservationRepository.sumOutstandingReservations(accountId))
                .thenReturn(new BigDecimal("0.60"));

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();
        AIRequest request = AIRequest.builder()
                .provider(Provider.OPENAI)
                .model("gpt-test")
                .prompt("hello")
                .build();

        assertThrows(PersonalBillingModeException.class,
                () -> service.reserve(context, request, "request-cap-test"));

        InOrder order = inOrder(creditService, ledgerRepository, reservationRepository);
        order.verify(creditService).lockWalletForUpdate(accountId);
        order.verify(ledgerRepository).sumCapturedSince(eq(accountId), any(LocalDateTime.class));
        order.verify(reservationRepository).sumOutstandingReservations(accountId);
        verify(creditService, never()).reserve(any(), any(), any(), any());
    }
}
