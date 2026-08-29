package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.*;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalCreditServiceImplTest {

    @Mock
    private PersonalCreditWalletRepository wallets;

    @Mock
    private PersonalCreditLedgerRepository ledger;

    @Mock
    private PersonalCreditReservationRepository reservations;

    private PersonalCreditServiceImpl service;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        service = new PersonalCreditServiceImpl(wallets, ledger, reservations);
        accountId = UUID.randomUUID();
    }

    @Test
    void creditAddsToWalletAndCreatesPurchaseLedgerEntry() {
        PersonalCreditWallet wallet = wallet("10.00", "0.00");
        when(wallets.findByPersonalAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(ledger.findByReferenceId("purchase-1")).thenReturn(Optional.empty());
        when(reservations.findByReferenceId("purchase-1")).thenReturn(Optional.empty());
        when(wallets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCreditLedger result = service.credit(
                accountId, new BigDecimal("5.25"), "purchase-1", "Initial credit purchase");

        assertEquals(new BigDecimal("15.25"), wallet.getBalance());
        assertEquals(PersonalCreditLedgerEntryType.PURCHASE, result.getEntryType());
        assertEquals(new BigDecimal("5.25"), result.getAmount());
        assertEquals("purchase-1", result.getReferenceId());
    }

    @Test
    void reservationUsesAvailableBalanceAndIncreasesReservedBalance() {
        PersonalCreditWallet wallet = wallet("10.00", "2.00");
        when(wallets.findByPersonalAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(ledger.findByReferenceId("request-1")).thenReturn(Optional.empty());
        when(reservations.findByReferenceId("request-1")).thenReturn(Optional.empty());
        when(reservations.save(any())).thenAnswer(invocation -> {
            PersonalCreditReservation r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(wallets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCreditReservation result = service.reserve(
                accountId, new BigDecimal("7.50"), "request-1", "Inference reservation");

        assertEquals(new BigDecimal("9.50"), wallet.getReservedBalance());
        assertEquals(new BigDecimal("0.50"), wallet.getAvailableBalance());
        assertEquals(PersonalCreditReservationStatus.RESERVED, result.getStatus());
        assertEquals(new BigDecimal("7.50"), result.getReservedAmount());
    }

    @Test
    void reservationRejectsInsufficientAvailableBalance() {
        PersonalCreditWallet wallet = wallet("10.00", "8.00");
        when(wallets.findByPersonalAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(ledger.findByReferenceId("request-2")).thenReturn(Optional.empty());
        when(reservations.findByReferenceId("request-2")).thenReturn(Optional.empty());

        assertThrows(PersonalCreditException.class, () -> service.reserve(
                accountId, new BigDecimal("2.01"), "request-2", "Too large"));
    }

    @Test
    void captureDeductsActualAmountAndReleasesUnusedReservation() {
        UUID reservationId = UUID.randomUUID();
        PersonalCreditWallet wallet = wallet("20.00", "5.00");
        PersonalCreditReservation reservation = reservation(reservationId, "5.00", "request-3");

        when(reservations.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(wallets.findByPersonalAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(wallets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCreditReservation result = service.capture(
                reservationId, new BigDecimal("3.20"));

        assertEquals(new BigDecimal("16.80"), wallet.getBalance());
        assertEquals(new BigDecimal("0.00"), wallet.getReservedBalance());
        assertEquals(new BigDecimal("3.20"), result.getCapturedAmount());
        assertEquals(PersonalCreditReservationStatus.CAPTURED, result.getStatus());
    }

    @Test
    void captureCannotExceedReservation() {
        UUID reservationId = UUID.randomUUID();
        PersonalCreditReservation reservation = reservation(reservationId, "5.00", "request-4");
        when(reservations.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(PersonalCreditException.class, () -> service.capture(
                reservationId, new BigDecimal("5.01")));
    }

    @Test
    void releaseReturnsReservedAmountWithoutReducingWalletBalance() {
        UUID reservationId = UUID.randomUUID();
        PersonalCreditWallet wallet = wallet("20.00", "5.00");
        PersonalCreditReservation reservation = reservation(reservationId, "5.00", "request-5");

        when(reservations.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));
        when(wallets.findByPersonalAccountIdForUpdate(accountId)).thenReturn(Optional.of(wallet));
        when(wallets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledger.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalCreditReservation result = service.release(reservationId);

        assertEquals(new BigDecimal("20.00"), wallet.getBalance());
        assertEquals(new BigDecimal("0.00"), wallet.getReservedBalance());
        assertEquals(PersonalCreditReservationStatus.RELEASED, result.getStatus());
    }

    @Test
    void completedReservationCannotBeCapturedAgain() {
        UUID reservationId = UUID.randomUUID();
        PersonalCreditReservation reservation = reservation(reservationId, "5.00", "request-6");
        reservation.setStatus(PersonalCreditReservationStatus.RELEASED);
        when(reservations.findByIdForUpdate(reservationId)).thenReturn(Optional.of(reservation));

        assertThrows(PersonalCreditException.class, () -> service.capture(
                reservationId, new BigDecimal("1.00")));
    }

    private PersonalCreditWallet wallet(String balance, String reserved) {
        return PersonalCreditWallet.builder()
                .id(UUID.randomUUID())
                .personalAccountId(accountId)
                .balance(new BigDecimal(balance))
                .reservedBalance(new BigDecimal(reserved))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private PersonalCreditReservation reservation(UUID id, String amount, String reference) {
        return PersonalCreditReservation.builder()
                .id(id)
                .personalAccountId(accountId)
                .reservedAmount(new BigDecimal(amount))
                .capturedAmount(BigDecimal.ZERO)
                .status(PersonalCreditReservationStatus.RESERVED)
                .referenceId(reference)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
