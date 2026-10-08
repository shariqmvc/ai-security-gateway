package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalCreditReservationRecoveryServiceTest {

    @Mock
    private PersonalCreditService creditService;
    @Mock
    private PersonalCreditExecutionService creditExecutionService;
    @Mock
    private PersonalCreditReservationRepository reservationRepository;

    @Test
    void retriesPendingSettlementUsingPersistedAmount() {
        UUID id = UUID.randomUUID();
        PersonalCreditReservation reservation = PersonalCreditReservation.builder()
                .id(id)
                .reservedAmount(new BigDecimal("1.00"))
                .settlementAmount(new BigDecimal("0.42"))
                .status(PersonalCreditReservationStatus.SETTLEMENT_PENDING)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        when(reservationRepository.findTop100ByStatusOrderByCreatedAtAsc(
                PersonalCreditReservationStatus.SETTLEMENT_PENDING)).thenReturn(List.of(reservation));
        when(reservationRepository.findTop100ByStatusAndProviderInvocationStartedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(PersonalCreditReservationStatus.RESERVED), any(LocalDateTime.class))).thenReturn(List.of());

        PersonalBillingProperties properties = new PersonalBillingProperties();
        PersonalCreditReservationRecoveryService recovery = new PersonalCreditReservationRecoveryService(
                creditExecutionService, creditService, reservationRepository, properties);

        recovery.recoverReservations();

        verify(creditExecutionService).recoverPendingSettlement(reservation);
    }

    @Test
    void doesNotReleaseOldReservationWhenProviderInvocationStarted() {
        PersonalCreditReservation reservation = PersonalCreditReservation.builder()
                .id(UUID.randomUUID())
                .reservedAmount(new BigDecimal("1.00"))
                .status(PersonalCreditReservationStatus.RESERVED)
                .providerInvocationStarted(true)
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        when(reservationRepository.findTop100ByStatusOrderByCreatedAtAsc(
                PersonalCreditReservationStatus.SETTLEMENT_PENDING)).thenReturn(List.of());
        when(reservationRepository.findTop100ByStatusAndProviderInvocationStartedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(PersonalCreditReservationStatus.RESERVED), any(LocalDateTime.class))).thenReturn(List.of());

        PersonalBillingProperties properties = new PersonalBillingProperties();
        PersonalCreditReservationRecoveryService recovery = new PersonalCreditReservationRecoveryService(
                creditService, reservationRepository, properties);

        recovery.recoverReservations();

        verify(creditService, never()).releaseStaleIfSafe(any(), any());
    }
}
