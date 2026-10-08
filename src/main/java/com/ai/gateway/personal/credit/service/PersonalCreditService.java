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

    PersonalCreditReservation capture(UUID reservationId, BigDecimal actualAmount);

    PersonalCreditReservation release(UUID reservationId);
}
