package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.*;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
import com.ai.gateway.personal.credit.exception.PersonalInsufficientCreditsException;
import com.ai.gateway.personal.billing.PersonalLowBalanceAlertService;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PersonalCreditServiceImpl implements PersonalCreditService {

    public PersonalCreditServiceImpl(
            PersonalCreditWalletRepository walletRepository,
            PersonalCreditLedgerRepository ledgerRepository,
            PersonalCreditReservationRepository reservationRepository) {
        this(walletRepository, ledgerRepository, reservationRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PersonalCreditServiceImpl(
            PersonalCreditWalletRepository walletRepository,
            PersonalCreditLedgerRepository ledgerRepository,
            PersonalCreditReservationRepository reservationRepository,
            PersonalLowBalanceAlertService lowBalanceAlertService) {
        this.walletRepository=walletRepository;
        this.ledgerRepository=ledgerRepository;
        this.reservationRepository=reservationRepository;
        this.lowBalanceAlertService=lowBalanceAlertService;
    }

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PersonalCreditWalletRepository walletRepository;
    private final PersonalCreditLedgerRepository ledgerRepository;
    private final PersonalCreditReservationRepository reservationRepository;
    private final PersonalLowBalanceAlertService lowBalanceAlertService;

    @Override
    @Transactional
    public PersonalCreditWallet getOrCreateWallet(UUID personalAccountId) {
        requireAccountId(personalAccountId);
        return walletRepository.findByPersonalAccountId(personalAccountId)
                .orElseGet(() -> walletRepository.save(PersonalCreditWallet.builder()
                        .personalAccountId(personalAccountId)
                        .balance(ZERO)
                        .reservedBalance(ZERO)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    @Override
    @Transactional
    public PersonalCreditWallet lockWalletForUpdate(UUID personalAccountId) {
        return getWalletForUpdate(personalAccountId);
    }

    @Override
    @Transactional
    public PersonalCreditLedger credit(UUID personalAccountId, BigDecimal amount,
                                       String referenceId, String description) {
        requirePositive(amount);
        requireReference(referenceId);

        PersonalCreditWallet wallet = getWalletForUpdate(personalAccountId);
        ensureReferenceUnused(referenceId);

        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        return ledgerRepository.save(PersonalCreditLedger.builder()
                .personalAccountId(personalAccountId)
                .entryType(PersonalCreditLedgerEntryType.PURCHASE)
                .amount(amount)
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public PersonalCreditLedger debit(UUID personalAccountId, BigDecimal amount,
                                      String referenceId, String description) {
        requirePositive(amount);
        requireReference(referenceId);

        PersonalCreditWallet wallet = getWalletForUpdate(personalAccountId);
        ensureReferenceUnused(referenceId);

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new PersonalCreditException("Insufficient AIRouter credits for reversal.");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        return ledgerRepository.save(PersonalCreditLedger.builder()
                .personalAccountId(personalAccountId)
                .entryType(PersonalCreditLedgerEntryType.REFUND)
                .amount(amount.negate())
                .referenceId(referenceId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public PersonalCreditReservation reserve(UUID personalAccountId, BigDecimal amount,
                                             String referenceId, String description) {
        requirePositive(amount);
        requireReference(referenceId);

        PersonalCreditWallet wallet = getWalletForUpdate(personalAccountId);
        ensureReferenceUnused(referenceId);

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new PersonalInsufficientCreditsException("Insufficient AIRouter credits.");
        }

        PersonalCreditReservation reservation = reservationRepository.save(
                PersonalCreditReservation.builder()
                        .personalAccountId(personalAccountId)
                        .reservedAmount(amount)
                        .capturedAmount(ZERO)
                        .status(PersonalCreditReservationStatus.RESERVED)
                        .referenceId(referenceId)
                        .createdAt(LocalDateTime.now())
                        .build());

        wallet.setReservedBalance(wallet.getReservedBalance().add(amount));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        ledgerRepository.save(PersonalCreditLedger.builder()
                .personalAccountId(personalAccountId)
                .entryType(PersonalCreditLedgerEntryType.RESERVATION)
                .amount(amount.negate())
                .referenceId(referenceId + ":reservation")
                .reservationId(reservation.getId())
                .description(description)
                .createdAt(LocalDateTime.now())
                .build());

        return reservation;
    }

    @Override
    @Transactional
    public PersonalCreditReservation markProviderInvocationSucceeded(
            UUID reservationId, String provider, String model,
            Integer inputTokens, Integer outputTokens) {
        requireReservationId(reservationId);
        if (inputTokens != null && inputTokens < 0 || outputTokens != null && outputTokens < 0) {
            throw new PersonalCreditException("Provider token usage cannot be negative.");
        }
        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() == PersonalCreditReservationStatus.CAPTURED) {
            return reservation;
        }
        if (reservation.getStatus() == PersonalCreditReservationStatus.SETTLEMENT_PENDING) {
            boolean sameUsage = java.util.Objects.equals(reservation.getSettlementProvider(), provider)
                    && java.util.Objects.equals(reservation.getSettlementModel(), model)
                    && java.util.Objects.equals(reservation.getSettlementInputTokens(), inputTokens)
                    && java.util.Objects.equals(reservation.getSettlementOutputTokens(), outputTokens);
            if (sameUsage) return reservation;
            throw new PersonalCreditException("A different provider settlement is already pending.");
        }
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED
                || !reservation.isProviderInvocationStarted()) {
            throw new PersonalCreditException("Provider success cannot be recorded for this reservation.");
        }

        reservation.setSettlementProvider(provider);
        reservation.setSettlementModel(model);
        reservation.setSettlementInputTokens(inputTokens == null ? 0 : inputTokens);
        reservation.setSettlementOutputTokens(outputTokens == null ? 0 : outputTokens);
        reservation.setStatus(PersonalCreditReservationStatus.SETTLEMENT_PENDING);
        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public PersonalCreditReservation prepareCapture(UUID reservationId, BigDecimal actualAmount) {
        requireReservationId(reservationId);
        if (actualAmount == null || actualAmount.compareTo(ZERO) < 0) {
            throw new PersonalCreditException("Actual credit amount cannot be negative.");
        }

        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() == PersonalCreditReservationStatus.CAPTURED) {
            if (reservation.getCapturedAmount() != null
                    && reservation.getCapturedAmount().compareTo(actualAmount) == 0) {
                return reservation;
            }
            throw new PersonalCreditException("Credit reservation was already captured with a different amount.");
        }
        if (reservation.getStatus() == PersonalCreditReservationStatus.SETTLEMENT_PENDING) {
            if (reservation.getSettlementAmount() != null
                    && reservation.getSettlementAmount().compareTo(actualAmount) == 0) {
                return reservation;
            }
            throw new PersonalCreditException("A different credit settlement is already pending.");
        }
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED) {
            throw new PersonalCreditException("Credit reservation is no longer active.");
        }
        if (actualAmount.compareTo(reservation.getReservedAmount()) > 0) {
            throw new PersonalCreditException("Actual credit amount exceeds reserved amount.");
        }

        reservation.setSettlementAmount(actualAmount);
        reservation.setStatus(PersonalCreditReservationStatus.SETTLEMENT_PENDING);
        return reservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public PersonalCreditReservation markProviderInvocationStarted(UUID reservationId) {
        requireReservationId(reservationId);
        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED) {
            throw new PersonalCreditException("Credit reservation is not ready for provider invocation.");
        }
        if (!reservation.isProviderInvocationStarted()) {
            reservation.setProviderInvocationStarted(true);
            reservationRepository.save(reservation);
        }
        return reservation;
    }

    @Override
    @Transactional
    public boolean releaseStaleIfSafe(UUID reservationId, LocalDateTime createdBefore) {
        requireReservationId(reservationId);
        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED
                || reservation.isProviderInvocationStarted()
                || reservation.getCreatedAt() == null
                || !reservation.getCreatedAt().isBefore(createdBefore)) {
            return false;
        }
        releaseReservedReservation(reservation);
        return true;
    }

    @Override
    @Transactional
    public PersonalCreditReservation capture(UUID reservationId, BigDecimal actualAmount) {
        requireReservationId(reservationId);
        if (actualAmount == null || actualAmount.compareTo(ZERO) < 0) {
            throw new PersonalCreditException("Actual credit amount cannot be negative.");
        }

        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() == PersonalCreditReservationStatus.CAPTURED) {
            if (reservation.getCapturedAmount() != null
                    && reservation.getCapturedAmount().compareTo(actualAmount) == 0) {
                // Repeated reconciliation of the same result is safe: the
                // original capture already committed its wallet and ledger mutations.
                return reservation;
            }
            throw new PersonalCreditException(
                    "Credit reservation was already captured with a different amount.");
        }
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED
                && reservation.getStatus() != PersonalCreditReservationStatus.SETTLEMENT_PENDING) {
            throw new PersonalCreditException("Credit reservation is no longer active.");
        }
        if (actualAmount.compareTo(reservation.getReservedAmount()) > 0) {
            throw new PersonalCreditException("Actual credit amount exceeds reserved amount.");
        }
        if (reservation.getStatus() == PersonalCreditReservationStatus.SETTLEMENT_PENDING
                && (reservation.getSettlementAmount() == null
                    || reservation.getSettlementAmount().compareTo(actualAmount) != 0)) {
            throw new PersonalCreditException("Capture amount does not match the pending settlement.");
        }

        PersonalCreditWallet wallet = getWalletForUpdate(reservation.getPersonalAccountId());
        BigDecimal release = reservation.getReservedAmount().subtract(actualAmount);

        wallet.setBalance(wallet.getBalance().subtract(actualAmount));
        wallet.setReservedBalance(wallet.getReservedBalance().subtract(reservation.getReservedAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        reservation.setCapturedAmount(actualAmount);
        reservation.setStatus(PersonalCreditReservationStatus.CAPTURED);
        reservation.setCompletedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        ledgerRepository.save(PersonalCreditLedger.builder()
                .personalAccountId(reservation.getPersonalAccountId())
                .entryType(PersonalCreditLedgerEntryType.CAPTURE)
                .amount(actualAmount.negate())
                .referenceId(reservation.getReferenceId() + ":capture")
                .reservationId(reservationId)
                .description("Inference credit capture")
                .createdAt(LocalDateTime.now())
                .build());

        if (release.compareTo(ZERO) > 0) {
            ledgerRepository.save(PersonalCreditLedger.builder()
                    .personalAccountId(reservation.getPersonalAccountId())
                    .entryType(PersonalCreditLedgerEntryType.RELEASE)
                    .amount(release)
                    .referenceId(reservation.getReferenceId() + ":release")
                    .reservationId(reservationId)
                    .description("Unused inference reservation released")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        if (lowBalanceAlertService != null) {
            lowBalanceAlertService.notifyIfNeeded(
                    reservation.getPersonalAccountId(),
                    wallet.getAvailableBalance());
        }

        return reservation;
    }

    @Override
    @Transactional
    public PersonalCreditReservation release(UUID reservationId) {
        requireReservationId(reservationId);

        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() == PersonalCreditReservationStatus.CAPTURED
                || reservation.getStatus() == PersonalCreditReservationStatus.RELEASED) {
            // Failure cleanup is idempotent after capture/release.
            return reservation;
        }
        if (reservation.getStatus() == PersonalCreditReservationStatus.SETTLEMENT_PENDING) {
            throw new PersonalCreditException(
                    "A pending credit settlement cannot be released before reconciliation.");
        }
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED) {
            throw new PersonalCreditException("Credit reservation is no longer active.");
        }

        releaseReservedReservation(reservation);
        return reservation;
    }

    private void releaseReservedReservation(PersonalCreditReservation reservation) {
        PersonalCreditWallet wallet = getWalletForUpdate(reservation.getPersonalAccountId());
        wallet.setReservedBalance(wallet.getReservedBalance().subtract(reservation.getReservedAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        reservation.setStatus(PersonalCreditReservationStatus.RELEASED);
        reservation.setCompletedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        ledgerRepository.save(PersonalCreditLedger.builder()
                .personalAccountId(reservation.getPersonalAccountId())
                .entryType(PersonalCreditLedgerEntryType.RELEASE)
                .amount(reservation.getReservedAmount())
                .referenceId(reservation.getReferenceId() + ":release")
                .reservationId(reservation.getId())
                .description("Inference reservation released")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private PersonalCreditWallet getWalletForUpdate(UUID personalAccountId) {
        requireAccountId(personalAccountId);
        return walletRepository.findByPersonalAccountIdForUpdate(personalAccountId)
                .orElseGet(() -> walletRepository.save(PersonalCreditWallet.builder()
                        .personalAccountId(personalAccountId)
                        .balance(ZERO)
                        .reservedBalance(ZERO)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    private void ensureReferenceUnused(String referenceId) {
        if (ledgerRepository.findByReferenceId(referenceId).isPresent()
                || reservationRepository.findByReferenceId(referenceId).isPresent()) {
            throw new PersonalCreditException("Credit reference has already been processed: " + referenceId);
        }
    }

    private void requireAccountId(UUID accountId) {
        if (accountId == null) throw new PersonalCreditException("Personal account id is required.");
    }

    private void requireReservationId(UUID reservationId) {
        if (reservationId == null) throw new PersonalCreditException("Reservation id is required.");
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new PersonalCreditException("Credit amount must be greater than zero.");
        }
    }

    private void requireReference(String referenceId) {
        if (referenceId == null || referenceId.isBlank()) {
            throw new PersonalCreditException("Credit reference is required.");
        }
    }
}
