package com.ai.gateway.personal.billing;

import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retries durable captures and releases only reservations that provably never
 * reached provider invocation. A reservation marked in-flight is never released
 * based on age alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalCreditReservationRecoveryService {

    private final PersonalCreditExecutionService creditExecutionService;
    private final PersonalCreditService creditService;
    private final PersonalCreditReservationRepository reservationRepository;
    private final PersonalBillingProperties properties;

    @Scheduled(fixedDelayString = "${alroute.personal.billing.reservation-recovery-interval-ms:60000}")
    public void recoverReservations() {
        recoverPendingSettlements();
        releaseAbandonedPreInvocationReservations();
    }

    void recoverPendingSettlements() {
        for (PersonalCreditReservation reservation : reservationRepository
                .findTop100ByStatusOrderByCreatedAtAsc(PersonalCreditReservationStatus.SETTLEMENT_PENDING)) {
            try {
                creditExecutionService.recoverPendingSettlement(reservation);
            } catch (RuntimeException ex) {
                log.warn("Credit settlement retry failed reservationId={}", reservation.getId(), ex);
            }
        }
    }

    @Scheduled(fixedDelayString = "${alroute.personal.billing.stale-inflight-audit-interval-ms:900000}")
    public void auditStaleInFlightReservations() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(Math.max(1, properties.getStaleInFlightReservationAuditAgeHours()));
        for (PersonalCreditReservation reservation : reservationRepository
                .findTop100ByStatusAndProviderInvocationStartedTrueAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PersonalCreditReservationStatus.RESERVED, cutoff)) {
            // Do not release: the provider may have completed while durable
            // settlement metadata failed to persist. Surface for reconciliation.
            log.error("Stale in-flight Personal credit reservation requires reconciliation; hold retained reservationId={} referenceId={} createdAt={}",
                    reservation.getId(), reservation.getReferenceId(), reservation.getCreatedAt());
        }
    }

    void releaseAbandonedPreInvocationReservations() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(Math.max(1, properties.getStaleReservationAgeMinutes()));
        for (PersonalCreditReservation reservation : reservationRepository
                .findTop100ByStatusAndProviderInvocationStartedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(
                        PersonalCreditReservationStatus.RESERVED, cutoff)) {
            try {
                creditService.releaseStaleIfSafe(reservation.getId(), cutoff);
            } catch (RuntimeException ex) {
                log.warn("Stale credit reservation recovery failed reservationId={}",
                        reservation.getId(), ex);
            }
        }
    }
}
