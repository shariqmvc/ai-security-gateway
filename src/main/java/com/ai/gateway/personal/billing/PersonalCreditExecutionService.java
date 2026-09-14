package com.ai.gateway.personal.billing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.cost.dto.PreRequestCostEstimate;
import com.ai.gateway.core.cost.dto.PreRequestCostRequest;
import com.ai.gateway.core.cost.service.PreRequestCostEstimator;
import com.ai.gateway.personal.credit.entity.PersonalCreditReservation;
import com.ai.gateway.personal.credit.service.PersonalCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns the financial side effects of Personal CREDIT inference.
 *
 * Reservation is created before provider invocation and reconciled after the
 * provider returns. BYOK and FREE never enter this service.
 */
@Service
@RequiredArgsConstructor
public class PersonalCreditExecutionService {

    private final PersonalCreditService creditService;
    private final PreRequestCostEstimator costEstimator;
    private final PersonalBillingProperties properties;

    public ReservationContext reserve(
            AuthenticationContext context,
            AIRequest request,
            String clientReference) {

        requirePersonal(context);

        int inputTokens = estimateInputTokens(request.getPrompt());
        int outputTokens = Math.max(0, properties.getReserveOutputTokens());

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

        try {
            int inputTokens = response != null && response.getUsage() != null
                    && response.getUsage().getInputTokens() != null
                    ? response.getUsage().getInputTokens() : 0;
            int outputTokens = response != null && response.getUsage() != null
                    && response.getUsage().getOutputTokens() != null
                    ? response.getUsage().getOutputTokens() : 0;

            PreRequestCostEstimate actual = costEstimator.estimate(
                    PreRequestCostRequest.builder()
                            .provider(request.getProvider())
                            .model(request.getModel())
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
             * A reservation is deliberately a hard ceiling. If the provider
             * reports usage above it, do not silently overdraw the wallet.
             * The reservation is captured at its ceiling and the caller gets
             * a deterministic billing exception so the discrepancy can be
             * reconciled explicitly.
             */
            if (actualAmount.compareTo(reservationContext.reservedAmount()) > 0) {
                creditService.capture(
                        reservationContext.reservationId(),
                        reservationContext.reservedAmount());
                throw new PersonalBillingModeException(
                        "Actual inference cost exceeded the reserved Personal credit amount.");
            }

            creditService.capture(
                    reservationContext.reservationId(),
                    actualAmount);
        } catch (PersonalBillingModeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            creditService.release(reservationContext.reservationId());
            throw ex;
        }
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
