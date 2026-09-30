package com.ai.gateway.personal.credit.repository;

import com.ai.gateway.personal.credit.entity.PersonalCreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalCreditLedgerRepository extends JpaRepository<PersonalCreditLedger, UUID> {

    Optional<PersonalCreditLedger> findByReferenceId(String referenceId);
 @Query(value="select coalesce(sum(-amount),0) from PERSONAL_CREDIT_LEDGER where personal_account_id=:accountId and entry_type='CAPTURE' and created_at>=:since",nativeQuery=true)
 BigDecimal sumCapturedSince(@Param("accountId") UUID accountId,@Param("since") LocalDateTime since);

    List<PersonalCreditLedger> findByPersonalAccountIdOrderByCreatedAtDesc(UUID personalAccountId);
}
