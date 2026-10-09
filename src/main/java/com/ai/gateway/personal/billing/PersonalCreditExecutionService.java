package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.dto.PreRequestCostRequest;
import com.ai.gateway.core.cost.service.PreRequestCostEstimator;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import com.ai.gateway.personal.billing.PersonalBillingSettingsRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditLedgerRepository;
import com.ai.gateway.personal.credit.repository.PersonalCreditReservationRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns the financial side effects of Personal CREDIT inference.
 *
 * Reservation is created before provider invocation and reconciled after the
 * provider returns. BYOK and FREE never enter this service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalCreditExecutionService {

    private final PersonalCreditService creditService;
    private final PreRequestCostEstimator costEstimator;
    private final PersonalBillingProperties properties;
    private final PersonalBillingSettingsRepository billingSettingsRepository;
    private final PersonalCreditLedgerRepository ledgerRepository;
    private final PersonalCreditReservationRepository reservationRepository;

    @Transactional
    public ReservationContext reserve(
            AuthenticationContext context,
            AIRequest request,
            String clientReference) {

        requirePersonal(context);

        int inputTokens = estimateInputTokens(request.getPrompt());
        // Reserve enough output headroom for normal provider variance. The
        // configured value remains an override when it is larger.
        int outputTokens = Math.max(4096, properties.getReserveOutputTokens());

        PreRequestCostEstimate estimate = costEstimator.estimate(
                PreRequestCostRequest.builder()
                        .provider(request.getProvider())
                        .model(request.getModel())
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .cachedInputTokens(0)
                        .build());

        BigDecimal reserveAmount = estimate.getTotalEstimatedCost()
                .multiply(properties.getReservationMultiplier());

        if (request.getMaximumRequestCost() != null
                && request.getMaximumRequestCost().compareTo(BigDecimal.ZERO) > 0) {
            reserveAmount = request.getMaximumRequestCost();
        }

        /*
         * Some providers (notably local Ollama models) are intentionally priced
         * at zero in the shared Core pricing catalog because their normal Personal
         * path is FREE. An explicit CREDIT request must nevertheless have a
         * positive reservation so that CREDIT remains a real billing mode.
         * Personal therefore applies its own small minimum transaction charge
         * without changing provider-neutral Core pricing or the FREE path.
         */
        BigDecimal minimumCreditCharge = minimumCreditCharge();
        if (reserveAmount.compareTo(BigDecimal.ZERO) <= 0) {
            reserveAmount = minimumCreditCharge;
        }

        /*
         * Serialize the spend-cap check on the account wallet row. The lock is
         * held by this transaction through reservation creation, so another
         * request for the same account cannot pass the cap check concurrently.
         */
        creditService.lockWalletForUpdate(context.getPersonalAccountId());

        var billingSettings = billingSettingsRepository
                .findByPersonalAccountId(context.getPersonalAccountId())
                .orElse(null);
        if (billingSettings != null && billingSettings.getMonthlySpendCap() != null) {
            LocalDateTime monthStart = LocalDateTime.now()
                    .withDayOfMonth(1)
                    .toLocalDate()
                    .atStartOfDay();
            BigDecimal capturedSpend = ledgerRepository.sumCapturedSince(
                    context.getPersonalAccountId(), monthStart);
            BigDecimal outstandingReservations = reservationRepository
                    .sumOutstandingReservations(context.getPersonalAccountId());
            BigDecimal committedSpend = capturedSpend.add(outstandingReservations);

            if (committedSpend.add(reserveAmount)
                    .compareTo(billingSettings.getMonthlySpendCap()) > 0) {
                throw new PersonalBillingModeException(
                        "Monthly Personal credit spend cap would be exceeded.");
            }
        }

        PersonalCreditReservation reservation =
                creditService.reserve(
                        context.getPersonalAccountId(),
                        reserveAmount,
                        clientReference,
                        "Personal CREDIT inference reservation");

        return new ReservationContext(reservation.getId(), reserveAmount);
    }

    public void reconcile(
            ReservationContext reservationContext,
            AIRequest request,
            AIResponse response) {

        if (reservationContext == null) {
            return;
        }

        Integer inputTokens = response != null && response.getUsage() != null
                ? response.getUsage().getInputTokens() : null;
        Integer outputTokens = response != null && response.getUsage() != null
                ? response.getUsage().getOutputTokens() : null;
        // Treat invalid negative counts as unavailable usage instead of leaving a
        // successful provider call stuck in RESERVED during settlement.
        if (inputTokens != null && inputTokens < 0) inputTokens = null;
        if (outputTokens != null && outputTokens < 0) outputTokens = null;
        boolean usageComplete = inputTokens != null && outputTokens != null;

        // Routing/failover may execute a different provider/model than the original
        // request. Persist and price the successful response target when available.
        Provider settledProvider = response != null && response.getProvider() != null
                ? response.getProvider() : request.getProvider();
        String settledModel = response != null && response.getModel() != null
                && !response.getModel().isBlank()
                ? response.getModel() : request.getModel();

        // Commit the successful provider result first. If cost estimation or
        // capture fails, the recovery worker still has the provider/model/usage.
        creditService.markProviderInvocationSucceeded(
                reservationContext.reservationId(),
                settledProvider == null ? null : settledProvider.name(),
                settledModel,
                inputTokens,
                outputTokens);

        if (!usageComplete) {
            // Missing or partial usage must never be interpreted as a free/near-free
            // inference. markProviderInvocationSucceeded atomically records the
            // reservation ceiling as settlement intent so recovery cannot recompute
            // a lower charge after a crash.
            log.warn("Provider response omitted complete token usage; settling at reserved ceiling reservationId={} provider={} model={} inputTokens={} outputTokens={}",
                    reservationContext.reservationId(), settledProvider, settledModel,
                    inputTokens, outputTokens);
            creditService.capture(
                    reservationContext.reservationId(), reservationContext.reservedAmount());
            return;
        }

        PreRequestCostEstimate actual = costEstimator.estimate(
                PreRequestCostRequest.builder()
                        .provider(settledProvider)
                        .model(settledModel)
                        .inputTokens(inputTokens)
                        .outputTokens(outputTokens)
                        .cachedInputTokens(0)
                        .build());

        BigDecimal actualAmount = actual.getTotalEstimatedCost();

        // Keep explicit CREDIT billable even when Core pricing for the
        // selected provider/model is zero (for example, local Ollama).
        if (actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
            actualAmount = minimumCreditCharge().min(reservationContext.reservedAmount());
        }

        /*
         * Persist settlement intent in its own transaction before wallet
         * mutation. If capture fails or the process exits, recovery can retry
         * this exact amount without recomputing usage or releasing the hold.
         */
        if (actualAmount.compareTo(reservationContext.reservedAmount()) > 0) {
            BigDecimal cappedAmount = reservationContext.reservedAmount();
            creditService.prepareCapture(reservationContext.reservationId(), cappedAmount);
            creditService.capture(reservationContext.reservationId(), cappedAmount);
            throw new PersonalBillingModeException(
                    "Actual inference cost exceeded the reserved Personal credit amount.");
        }

        creditService.prepareCapture(reservationContext.reservationId(), actualAmount);
        creditService.capture(reservationContext.reservationId(), actualAmount);
    }

    public void markProviderInvocationStarted(ReservationContext reservationContext) {
        if (reservationContext != null) {
            creditService.markProviderInvocationStarted(reservationContext.reservationId());
        }
    }

    public void recoverPendingSettlement(PersonalCreditReservation reservation) {
        if (reservation == null
                || reservation.getStatus() != com.ai.gateway.personal.credit.entity.PersonalCreditReservationStatus.SETTLEMENT_PENDING) {
            return;
        }
        if (reservation.getSettlementAmount() != null) {
            creditService.capture(reservation.getId(), reservation.getSettlementAmount());
            return;
        }
        if (reservation.getSettlementProvider() == null || reservation.getSettlementModel() == null) {
            throw new PersonalBillingModeException(
                    "Pending credit settlement is missing provider usage metadata.");
        }

        Provider provider = Provider.valueOf(reservation.getSettlementProvider());
        PreRequestCostEstimate estimate = costEstimator.estimate(
                PreRequestCostRequest.builder()
                        .provider(provider)
                        .model(reservation.getSettlementModel())
                        .inputTokens(reservation.getSettlementInputTokens() == null
                                ? 0 : reservation.getSettlementInputTokens())
                        .outputTokens(reservation.getSettlementOutputTokens() == null
                                ? 0 : reservation.getSettlementOutputTokens())
                        .cachedInputTokens(0)
                        .build());
        BigDecimal actualAmount = estimate.getTotalEstimatedCost();
        if (actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
            actualAmount = minimumCreditCharge().min(reservation.getReservedAmount());
        }
        if (actualAmount.compareTo(reservation.getReservedAmount()) > 0) {
            log.error("Recovered inference cost exceeds reservation; capturing reserved ceiling reservationId={} estimatedAmount={} reservedAmount={}",
                    reservation.getId(), actualAmount, reservation.getReservedAmount());
            actualAmount = reservation.getReservedAmount();
        }
        creditService.prepareCapture(reservation.getId(), actualAmount);
        creditService.capture(reservation.getId(), actualAmount);
    }

    public void releaseOnFailure(ReservationContext reservationContext) {
        if (reservationContext != null) {
            creditService.release(reservationContext.reservationId());
        }
    }

    private void requirePersonal(AuthenticationContext context) {
        if (context == null || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new PersonalBillingModeException(
                    "Personal authentication is required for credit billing.");
        }
    }

    private BigDecimal minimumCreditCharge() {
        BigDecimal minimum = properties.getMinimumCreditCharge();
        if (minimum == null || minimum.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PersonalBillingModeException(
                    "Personal minimum credit charge must be positive.");
        }
        return minimum;
    }

    private int estimateInputTokens(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return 1;
        }
        // Conservative deterministic fallback when no provider tokenizer is available.
        return Math.max(1, (prompt.length() + 3) / 4);
    }

    public record ReservationContext(UUID reservationId, BigDecimal reservedAmount) {}
}
