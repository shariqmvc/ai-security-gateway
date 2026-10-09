package com.ai.gateway.personal.credit.service;

import com.ai.gateway.personal.billing.PersonalCreditExecutionService;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus;
import com.ai.gateway.personal.credit.entity.PersonalCreditWallet;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditWalletRepository;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest
class PersonalCreditSettlementConcurrencyIntegrationTest {

    @Autowired
    private PersonalCreditService creditService;

    @Autowired
    private PersonalCreditExecutionService creditExecutionService;

    @Autowired
    private PersonalCreditReservationRepository reservationRepository;

    @Autowired
    private PersonalCreditWalletRepository walletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID accountId;
    private UUID userId;
    private UUID reservationId;

    @AfterEach
    void cleanup() {
        if (accountId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM PERSONAL_ACCOUNTS WHERE id = ?", accountId);
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM PERSONAL_USERS WHERE id = ?", userId);
        }
    }

    @Test
    void concurrentCaptureAndRecoveryRetryDebitWalletAndWriteCaptureLedgerOnlyOnce() throws Exception {
        PersonalCreditReservation pending = createPendingSettlement();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> capture = executor.submit(() -> {
                ready.countDown();
                await(start);
                creditService.capture(reservationId, new BigDecimal("3.20"));
            });
            Future<?> recovery = executor.submit(() -> {
                ready.countDown();
                await(start);
                // Use the real recovery path with the persisted settlement amount.
                creditExecutionService.recoverPendingSettlement(pending);
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both workers should be ready to race");
            start.countDown();
            capture.get(20, TimeUnit.SECONDS);
            recovery.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Workers should terminate");
        }

        PersonalCreditWallet wallet = walletRepository.findByPersonalAccountId(accountId).orElseThrow();
        PersonalCreditReservation settled = reservationRepository.findById(reservationId).orElseThrow();
        long captureEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERSONAL_CREDIT_LEDGER WHERE reservation_id = ? AND entry_type = 'CAPTURE'",
                Long.class,
                reservationId);

        assertEquals(PersonalCreditReservationStatus.CAPTURED, settled.getStatus());
        assertEquals(0, settled.getCapturedAmount().compareTo(new BigDecimal("3.20")));
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("16.80")));
        assertEquals(0, wallet.getReservedBalance().compareTo(BigDecimal.ZERO));
        assertEquals(1L, captureEntries, "Only one capture ledger entry may be written");
    }

    @Test
    void twoConcurrentRecoveryWorkersSettlePendingReservationOnlyOnce() throws Exception {
        PersonalCreditReservation pending = createPendingSettlement();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                await(start);
                creditExecutionService.recoverPendingSettlement(pending);
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                await(start);
                creditExecutionService.recoverPendingSettlement(pending);
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both recovery workers should be ready");
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Workers should terminate");
        }

        PersonalCreditWallet wallet = walletRepository.findByPersonalAccountId(accountId).orElseThrow();
        PersonalCreditReservation settled = reservationRepository.findById(reservationId).orElseThrow();
        long captureEntries = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PERSONAL_CREDIT_LEDGER WHERE reservation_id = ? AND entry_type = 'CAPTURE'",
                Long.class,
                reservationId);

        assertEquals(PersonalCreditReservationStatus.CAPTURED, settled.getStatus());
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("16.80")));
        assertEquals(0, wallet.getReservedBalance().compareTo(BigDecimal.ZERO));
        assertEquals(1L, captureEntries, "Concurrent recovery must not duplicate capture ledger entries");
    }

    private PersonalCreditReservation createPendingSettlement() {
        userId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        String uniqueEmail = "credit-concurrency-" + userId + "@example.test";
        jdbcTemplate.update(
                "INSERT INTO PERSONAL_USERS (id, email, password_hash, display_name, status, email_verified, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, uniqueEmail, "integration-test-hash", "Credit concurrency test", "ACTIVE", true);
        jdbcTemplate.update(
                "INSERT INTO PERSONAL_ACCOUNTS (id, user_id, plan, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                accountId, userId, "PERSONAL_FREE", "ACTIVE");

        creditService.credit(accountId, new BigDecimal("20.00"),
                "concurrency-fund-" + accountId, "Concurrency integration test funding");
        PersonalCreditReservation reservation = creditService.reserve(
                accountId, new BigDecimal("5.00"),
                "concurrency-request-" + accountId, "Concurrency integration test reservation");
        reservationId = reservation.getId();

        creditService.markProviderInvocationStarted(reservationId);
        creditService.markProviderInvocationSucceeded(reservationId, "OPENAI", "gpt-test", 100, 50);
        creditService.prepareCapture(reservationId, new BigDecimal("3.20"));

        PersonalCreditReservation pending = reservationRepository.findById(reservationId).orElseThrow();
        assertNotNull(pending.getSettlementAmount());
        assertEquals(PersonalCreditReservationStatus.SETTLEMENT_PENDING, pending.getStatus());
        return pending;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test start");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for concurrent test start", ex);
        }
    }
}
