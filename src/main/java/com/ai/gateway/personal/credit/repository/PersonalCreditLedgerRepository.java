package com.ai.gateway.personal.credit.repository;

import com.ai.gateway.personal.credit.entity.PersonalCreditLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalCreditLedgerRepository extends JpaRepository<PersonalCreditLedger, UUID> {

    Optional<PersonalCreditLedger> findByReferenceId(String referenceId);

    List<PersonalCreditLedger> findByPersonalAccountIdOrderByCreatedAtDesc(UUID personalAccountId);
}
