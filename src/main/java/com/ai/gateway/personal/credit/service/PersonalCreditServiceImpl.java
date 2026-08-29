package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.credit.entity.*;
import com.ai.gateway.personal.credit.exception.PersonalCreditException;
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
@RequiredArgsConstructor
public class PersonalCreditServiceImpl implements PersonalCreditService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PersonalCreditWalletRepository walletRepository;
    private final PersonalCreditLedgerRepository ledgerRepository;
    private final PersonalCreditReservationRepository reservationRepository;

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
    public PersonalCreditReservation reserve(UUID personalAccountId, BigDecimal amount,
                                             String referenceId, String description) {
        requirePositive(amount);
        requireReference(referenceId);

        PersonalCreditWallet wallet = getWalletForUpdate(personalAccountId);
        ensureReferenceUnused(referenceId);

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new PersonalCreditException("Insufficient AIRouter credits.");
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
    public PersonalCreditReservation capture(UUID reservationId, BigDecimal actualAmount) {
        requireReservationId(reservationId);
        if (actualAmount == null || actualAmount.compareTo(ZERO) < 0) {
            throw new PersonalCreditException("Actual credit amount cannot be negative.");
        }

        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED) {
            throw new PersonalCreditException("Credit reservation is no longer active.");
        }
        if (actualAmount.compareTo(reservation.getReservedAmount()) > 0) {
            throw new PersonalCreditException("Actual credit amount exceeds reserved amount.");
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

        return reservation;
    }

    @Override
    @Transactional
    public PersonalCreditReservation release(UUID reservationId) {
        requireReservationId(reservationId);

        PersonalCreditReservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new PersonalCreditException("Credit reservation not found."));
        if (reservation.getStatus() != PersonalCreditReservationStatus.RESERVED) {
            throw new PersonalCreditException("Credit reservation is no longer active.");
        }

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
                .reservationId(reservationId)
                .description("Inference reservation released")
                .createdAt(LocalDateTime.now())
                .build());

        return reservation;
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
