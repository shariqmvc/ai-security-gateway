package com.ai.gateway.personal.credit.repository;

import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PersonalCreditReservationRepository extends JpaRepository<PersonalCreditReservation, UUID> {

    @Query("""
            select coalesce(sum(r.reservedAmount), 0)
            from PersonalCreditReservation r
            where r.personalAccountId = :accountId
              and r.status = com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus.RESERVED
            """)
    BigDecimal sumOutstandingReservations(@Param("accountId") UUID accountId);

    Optional<PersonalCreditReservation> findByReferenceId(String referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from PersonalCreditReservation r
            where r.id = :reservationId
            """)
    Optional<PersonalCreditReservation> findByIdForUpdate(
            @Param("reservationId") UUID reservationId);
}
