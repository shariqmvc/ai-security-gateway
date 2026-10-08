package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.PersonalCreditLedger;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;

import java.math.BigDecimal;
import java.util.UUID;

public interface PersonalCreditService {

    PersonalCreditWallet getOrCreateWallet(UUID personalAccountId);

    /**
     * Acquires the account wallet row lock for the duration of the caller's
     * transaction so account-scoped billing checks can be serialized.
     */
    PersonalCreditWallet lockWalletForUpdate(UUID personalAccountId);

    PersonalCreditLedger credit(UUID personalAccountId, BigDecimal amount,
                                String referenceId, String description);

    PersonalCreditLedger debit(UUID personalAccountId, BigDecimal amount,
                               String referenceId, String description);

    PersonalCreditReservation reserve(UUID personalAccountId, BigDecimal amount,
                                      String referenceId, String description);

    /** Persist the intended charge before attempting the wallet/ledger capture. */
    PersonalCreditReservation markProviderInvocationSucceeded(
            UUID reservationId, String provider, String model,
            Integer inputTokens, Integer outputTokens);

    PersonalCreditReservation prepareCapture(UUID reservationId, BigDecimal actualAmount);

    /** Mark the reservation before invoking the provider so recovery cannot release an active request. */
    PersonalCreditReservation markProviderInvocationStarted(UUID reservationId);

    /** Release only if the reservation is still old and provider invocation never started. */
    boolean releaseStaleIfSafe(UUID reservationId, java.time.LocalDateTime createdBefore);

    PersonalCreditReservation capture(UUID reservationId, BigDecimal actualAmount);

    PersonalCreditReservation release(UUID reservationId);
}
