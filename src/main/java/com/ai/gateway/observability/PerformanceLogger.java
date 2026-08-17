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
}
