package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalCreditReconciliationServiceTest {

    private PersonalCreditWalletRepository wallets;
    private PersonalCreditLedgerRepository ledger;
    private PersonalCreditReservationRepository reservations;
    private PersonalCreditReconciliationService service;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        wallets = mock(PersonalCreditWalletRepository.class);
        ledger = mock(PersonalCreditLedgerRepository.class);
        reservations = mock(PersonalCreditReservationRepository.class);
        service = new PersonalCreditReconciliationService(wallets, ledger, reservations);
        accountId = UUID.randomUUID();
    }

    @Test
    void reportsMatchingWalletAndReservationBalances() {
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.of(wallet("10.00", "2.00")));
        when(ledger.sumBalanceAffectingEntries(accountId)).thenReturn(new BigDecimal("10.00"));
        when(ledger.countByPersonalAccountId(accountId)).thenReturn(3L);
        when(reservations.sumOutstandingReservations(accountId)).thenReturn(new BigDecimal("2.00"));

        WalletReconciliationReport report = service.reconcileAccount(accountId);

        assertEquals(WalletReconciliationReport.CheckStatus.MATCH, report.balanceStatus());
        assertEquals(WalletReconciliationReport.CheckStatus.MATCH, report.reservedBalanceStatus());
        assertTrue(report.isFullyReconciled());
        assertEquals(new BigDecimal("0.00"), report.balanceVariance());
        assertEquals(new BigDecimal("0.00"), report.reservedBalanceVariance());
        verify(wallets).findByPersonalAccountId(accountId);
        verify(ledger).sumBalanceAffectingEntries(accountId);
        verify(ledger).countByPersonalAccountId(accountId);
        verify(reservations).sumOutstandingReservations(accountId);
        verifyNoMoreInteractions(wallets, ledger, reservations);
    }

    @Test
    void reportsBalanceMismatchWithoutMutatingFinancialRecords() {
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.of(wallet("12.00", "0.00")));
        when(ledger.sumBalanceAffectingEntries(accountId)).thenReturn(new BigDecimal("10.00"));
        when(ledger.countByPersonalAccountId(accountId)).thenReturn(2L);
        when(reservations.sumOutstandingReservations(accountId)).thenReturn(BigDecimal.ZERO);

        WalletReconciliationReport report = service.reconcileAccount(accountId);

        assertEquals(WalletReconciliationReport.CheckStatus.MISMATCH, report.balanceStatus());
        assertEquals(new BigDecimal("2.00"), report.balanceVariance());
        assertFalse(report.isFullyReconciled());
        verify(wallets, never()).save(any());
        verify(ledger, never()).save(any());
        verify(reservations, never()).save(any());
    }

    @Test
    void reportsReservedBalanceMismatch() {
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.of(wallet("10.00", "5.00")));
        when(ledger.sumBalanceAffectingEntries(accountId)).thenReturn(new BigDecimal("10.00"));
        when(ledger.countByPersonalAccountId(accountId)).thenReturn(1L);
        when(reservations.sumOutstandingReservations(accountId)).thenReturn(new BigDecimal("3.00"));

        WalletReconciliationReport report = service.reconcileAccount(accountId);

        assertEquals(WalletReconciliationReport.CheckStatus.MATCH, report.balanceStatus());
        assertEquals(WalletReconciliationReport.CheckStatus.MISMATCH, report.reservedBalanceStatus());
        assertEquals(new BigDecimal("2.00"), report.reservedBalanceVariance());
    }

    @Test
    void doesNotDeclareLegacyNonZeroBalanceMismatchWithoutLedgerHistory() {
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.of(wallet("7.00", "0.00")));
        when(ledger.sumBalanceAffectingEntries(accountId)).thenReturn(BigDecimal.ZERO);
        when(ledger.countByPersonalAccountId(accountId)).thenReturn(0L);
        when(reservations.sumOutstandingReservations(accountId)).thenReturn(BigDecimal.ZERO);

        WalletReconciliationReport report = service.reconcileAccount(accountId);

        assertEquals(WalletReconciliationReport.CheckStatus.INSUFFICIENT_HISTORY, report.balanceStatus());
        assertNull(report.ledgerBalance());
        assertNull(report.balanceVariance());
        assertFalse(report.isFullyReconciled());
    }

    @Test
    void rejectsUnknownAccountWallet() {
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.empty());

        assertThrows(PersonalCreditException.class, () -> service.reconcileAccount(accountId));
        verifyNoInteractions(ledger, reservations);
    }

    @Test
    void reconcileAllAccountsReturnsOneReportPerWallet() {
        PersonalCreditWallet wallet = wallet("10.00", "0.00");
        when(wallets.findAll()).thenReturn(List.of(wallet));
        when(wallets.findByPersonalAccountId(accountId)).thenReturn(Optional.of(wallet));
        when(ledger.sumBalanceAffectingEntries(accountId)).thenReturn(new BigDecimal("10.00"));
        when(ledger.countByPersonalAccountId(accountId)).thenReturn(1L);
        when(reservations.sumOutstandingReservations(accountId)).thenReturn(BigDecimal.ZERO);

        List<WalletReconciliationReport> reports = service.reconcileAllAccounts();

        assertEquals(1, reports.size());
        assertTrue(reports.get(0).isFullyReconciled());
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
}
