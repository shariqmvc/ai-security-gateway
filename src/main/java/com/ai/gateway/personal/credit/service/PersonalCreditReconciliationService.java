package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditWalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only diagnostics for comparing wallet balances with ledger entries and outstanding
 * reservations. This service reports discrepancies and never repairs financial records.
 */
@Service
public class PersonalCreditReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PersonalCreditWalletRepository walletRepository;
    private final PersonalCreditLedgerRepository ledgerRepository;
    private final PersonalCreditReservationRepository reservationRepository;

    public PersonalCreditReconciliationService(
            PersonalCreditWalletRepository walletRepository,
            PersonalCreditLedgerRepository ledgerRepository,
            PersonalCreditReservationRepository reservationRepository) {
        this.walletRepository = walletRepository;
        this.ledgerRepository = ledgerRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public WalletReconciliationReport reconcileAccount(UUID personalAccountId) {
        if (personalAccountId == null) {
            throw new PersonalCreditException("Personal account id is required.");
        }
        PersonalCreditWallet wallet = walletRepository.findByPersonalAccountId(personalAccountId)
                .orElseThrow(() -> new PersonalCreditException("Credit wallet not found."));

        BigDecimal actualBalance = zeroIfNull(wallet.getBalance());
        BigDecimal actualReserved = zeroIfNull(wallet.getReservedBalance());
        BigDecimal ledgerBalance = zeroIfNull(
                ledgerRepository.sumBalanceAffectingEntries(personalAccountId));
        long ledgerEntryCount = ledgerRepository.countByPersonalAccountId(personalAccountId);

        WalletReconciliationReport.CheckStatus balanceStatus;
        BigDecimal balanceVariance;
        if (ledgerEntryCount == 0 && actualBalance.compareTo(ZERO) != 0) {
            // Do not call a possibly legacy/pre-ledger balance a mismatch without history.
            balanceStatus = WalletReconciliationReport.CheckStatus.INSUFFICIENT_HISTORY;
            balanceVariance = null;
            ledgerBalance = null;
        } else {
            balanceVariance = actualBalance.subtract(ledgerBalance);
            balanceStatus = balanceVariance.compareTo(ZERO) == 0
                    ? WalletReconciliationReport.CheckStatus.MATCH
                    : WalletReconciliationReport.CheckStatus.MISMATCH;
        }

        BigDecimal outstanding = zeroIfNull(
                reservationRepository.sumOutstandingReservations(personalAccountId));
        BigDecimal reservedVariance = actualReserved.subtract(outstanding);
        WalletReconciliationReport.CheckStatus reservedStatus =
                reservedVariance.compareTo(ZERO) == 0
                        ? WalletReconciliationReport.CheckStatus.MATCH
                        : WalletReconciliationReport.CheckStatus.MISMATCH;

        return new WalletReconciliationReport(
                personalAccountId,
                actualBalance,
                ledgerBalance,
                balanceVariance,
                balanceStatus,
                actualReserved,
                outstanding,
                reservedVariance,
                reservedStatus,
                ledgerEntryCount);
    }

    @Transactional(readOnly = true)
    public List<WalletReconciliationReport> reconcileAllAccounts() {
        return walletRepository.findAll().stream()
                .map(wallet -> reconcileAccount(wallet.getPersonalAccountId()))
                .toList();
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
