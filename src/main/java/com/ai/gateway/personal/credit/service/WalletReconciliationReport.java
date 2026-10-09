package com.ai.gateway.personal.credit.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only snapshot comparing persisted wallet values with ledger/reservation-derived values.
 * A null ledger balance/variance means there is insufficient ledger history to validate a
 * non-zero wallet balance; reconciliation never mutates wallet, ledger, or reservation data.
 */
public record WalletReconciliationReport(
        UUID personalAccountId,
        BigDecimal actualBalance,
        BigDecimal ledgerBalance,
        BigDecimal balanceVariance,
        CheckStatus balanceStatus,
        BigDecimal actualReservedBalance,
        BigDecimal outstandingReservationAmount,
        BigDecimal reservedBalanceVariance,
        CheckStatus reservedBalanceStatus,
        long ledgerEntryCount) {

    public enum CheckStatus {
        MATCH,
        MISMATCH,
        INSUFFICIENT_HISTORY
    }

    public boolean isFullyReconciled() {
        return balanceStatus == CheckStatus.MATCH
                && reservedBalanceStatus == CheckStatus.MATCH;
    }
}
