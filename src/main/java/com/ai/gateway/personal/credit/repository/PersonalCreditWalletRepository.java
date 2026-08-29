package com.ai.gateway.personal.credit.repository;

import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PersonalCreditWalletRepository extends JpaRepository<PersonalCreditWallet, UUID> {

    Optional<PersonalCreditWallet> findByPersonalAccountId(UUID personalAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w
            from PersonalCreditWallet w
            where w.personalAccountId = :personalAccountId
            """)
    Optional<PersonalCreditWallet> findByPersonalAccountIdForUpdate(
            @Param("personalAccountId") UUID personalAccountId);
}
