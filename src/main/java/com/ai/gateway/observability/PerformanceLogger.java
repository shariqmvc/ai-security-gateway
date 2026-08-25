package com.ai.gateway.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Central performance/event logger. Messages intentionally contain metadata
 * only; prompts, responses, API keys and provider credentials must not be
 * written to the performance log.
 */
@Component
public class PerformanceLogger {

    private static final Logger LOG =
            LoggerFactory.getLogger("com.ai.gateway.performance");

    public void requestStart(UUID requestId, String endpoint) {
        LOG.info("event=REQUEST_START requestId={} endpoint={}", requestId, endpoint);
    }

    public void stage(
            String stage,
            UUID requestId,
            long durationMs,
            String outcome) {
        LOG.info(
                "event=STAGE_COMPLETED stage={} requestId={} durationMs={} outcome={}",
                stage,
                requestId,
                durationMs,
                outcome);
    }

    public void providerStart(
            UUID requestId,
            String provider,
            String model,
            int attempt) {
        LOG.info(
                "event=PROVIDER_REQUEST_START requestId={} provider={} model={} attempt={}",
                requestId,
                provider,
                model,
                attempt);
    }

    public void providerCompleted(
            UUID requestId,
            String provider,
            String model,
            int attempt,
            long durationMs,
            String outcome) {
        LOG.info(
                "event=PROVIDER_REQUEST_COMPLETED requestId={} provider={} model={} attempt={} durationMs={} outcome={}",
                requestId,
                provider,
                model,
                attempt,
                durationMs,
                outcome);
    }

    /**
     * Provider-native inference telemetry. Durations are supplied by Ollama
     * in nanoseconds and converted here to milliseconds. No prompt/response
     * content is logged.
     */
    public void providerTelemetry(
            UUID requestId,
            String provider,
            String model,
            int attempt,
            Integer inputTokens,
            Integer outputTokens,
            Long totalDurationNs,
            Long loadDurationNs,
            Long promptEvalDurationNs,
            Long evalDurationNs) {

        Double tokensPerSecond = null;
        if (outputTokens != null
                && outputTokens > 0
                && evalDurationNs != null
                && evalDurationNs > 0) {
            tokensPerSecond =
                    (outputTokens * 1_000_000_000.0) / evalDurationNs;
        }

        LOG.info(
                "event=PROVIDER_INFERENCE_TELEMETRY requestId={} provider={} model={} attempt={} inputTokens={} outputTokens={} totalDurationMs={} modelLoadMs={} promptEvalMs={} generationMs={} tokensPerSecond={}",
                requestId,
                provider,
                model,
                attempt,
                inputTokens,
                outputTokens,
                nanosToMs(totalDurationNs),
                nanosToMs(loadDurationNs),
                nanosToMs(promptEvalDurationNs),
                nanosToMs(evalDurationNs),
                tokensPerSecond);
    }

    private Long nanosToMs(Long nanos) {
        return nanos == null ? null : nanos / 1_000_000L;
    }

    public void failover(
            UUID requestId,
            String fromProvider,
            String toProvider,
            int attempt) {
        LOG.info(
                "event=PROVIDER_FAILOVER requestId={} fromProvider={} toProvider={} attempt={}",
                requestId,
                fromProvider,
                toProvider,
                attempt);
    }

    public void routingDecision(
            UUID requestId,
            String strategy,
            String provider,
            String model,
            Double selectedScore,
            Integer selectedRank,
            Integer candidateCount,
            String selectionReason,
            String routingSignals) {
        LOG.info(
                "event=ROUTING_DECISION requestId={} strategy={} provider={} model={} selectedScore={} selectedRank={} candidateCount={} selectionReason={} signals={}",
                requestId,
                strategy,
                provider,
                model,
                selectedScore,
                selectedRank,
                candidateCount,
                selectionReason,
                routingSignals);
    }

    public void requestCompleted(
            UUID requestId,
            long totalLatencyMs,
            String provider,
            String model,
            String outcome) {
        requestCompleted(
                requestId,
                totalLatencyMs,
                provider,
                model,
                outcome,
                null,
                null);
    }

    public void requestCompleted(
            UUID requestId,
            long totalLatencyMs,
            String provider,
            String model,
            String outcome,
            Long providerLatencyMs,
            Long gatewayOverheadMs) {
        LOG.info(
                "event=REQUEST_COMPLETED requestId={} provider={} model={} totalLatencyMs={} providerLatencyMs={} gatewayOverheadMs={} outcome={}",
                requestId,
                provider,
                model,
                totalLatencyMs,
                providerLatencyMs,
                gatewayOverheadMs,
                outcome);
    }

    public void providerConcurrencyWait(
            UUID requestId,
            String provider,
            int maxConcurrent,
            long waitBudgetMs,
            int active) {

        LOG.info(
                "event=PROVIDER_CONCURRENCY_WAIT requestId={} provider={} maxConcurrent={} waitBudgetMs={} active={}",
                requestId,
                provider,
                maxConcurrent,
                waitBudgetMs,
                active
        );
    }

    public void providerConcurrencyAcquired(
            UUID requestId,
            String provider,
            int maxConcurrent,
            int active,
            long waitMs) {

        LOG.info(
                "event=PROVIDER_CONCURRENCY_ACQUIRED requestId={} provider={} maxConcurrent={} active={} waitMs={}",
                requestId,
                provider,
                maxConcurrent,
                active,
                waitMs
        );
    }

    public void providerConcurrencyReleased(
            UUID requestId,
            String provider,
            int maxConcurrent,
            int active) {

        LOG.info(
                "event=PROVIDER_CONCURRENCY_RELEASED requestId={} provider={} maxConcurrent={} active={}",
                requestId,
                provider,
                maxConcurrent,
                active
        );
    }

    public void providerConcurrencyRejected(
            UUID requestId,
            String provider,
            int maxConcurrent,
            long waitMs,
            int active) {

        LOG.warn(
                "event=PROVIDER_CONCURRENCY_REJECTED requestId={} provider={} maxConcurrent={} waitMs={} active={}",
                requestId,
                provider,
                maxConcurrent,
                waitMs,
                active
        );
    }
}
