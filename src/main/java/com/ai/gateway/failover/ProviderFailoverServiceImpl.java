package com.ai.gateway.failover;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.observability.PerformanceLogger;
import org.slf4j.MDC;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.health.RoutingHealthService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provider execution boundary with bounded, ordered failover.
 *
 * Governance checks are intentionally outside this service. This service is
 * only entered after authentication, firewall, policy, PII masking and
 * entitlement validation have completed successfully.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderFailoverServiceImpl implements ProviderFailoverService {

    private final AIProviderFactory providerFactory;
    private final ProviderModelRegistryService providerModelRegistryService;
    private final FailoverProperties properties;
    private final GatewayMetricsService metricsService;
    private final RoutingAnalyticsService routingAnalyticsService;
    private final PerformanceLogger performanceLogger;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RoutingHealthService routingHealthService;

    @Override
    public AIResponse execute(AIRequest request) {

        if (request == null || request.getProvider() == null) {
            throw new IllegalArgumentException(
                    "AI request and primary provider are required.");
        }

        /*
         * Failover is disabled: execute the primary provider directly.
         * No failover metrics or analytics are recorded.
         */
        if (!properties.isEnabled()) {
            return invoke(request, 1);
        }

        int maxAttempts = Math.max(1, properties.getMaxAttempts());

        Set<Provider> attempted = new HashSet<>();
        Throwable primaryFailure = null;
        Throwable lastFailure = null;

        Provider primary = request.getProvider();
        List<Provider> fallbacks =
                properties.fallbacksFor(primary);
        log.info(
                "FAILOVER_PLAN requestId={} primary={} enabled={} maxAttempts={} configuredFallbacks={}",
                requestId(),
                primary,
                properties.isEnabled(),
                maxAttempts,
                fallbacks
        );
        /*
         * Attempt #1: primary provider.
         */
        attempted.add(primary);

        try {
            return invoke(request, 1);

        } catch (Exception ex) {

            primaryFailure = ex;
            lastFailure = ex;

            recordProviderFailure(request, ex);

            if (!isRetryableFailure(ex)) {
                log.info(
                        "FAILOVER_NOT_RETRYABLE requestId={} provider={} failureType={}",
                        requestId(),
                        primary,
                        ex.getClass().getSimpleName());
                throw propagate(primaryFailure, lastFailure);
            }
        }
        log.info(
                "FAILOVER_DECISION requestId={} primary={} primaryFailure={} maxAttempts={} fallbackCount={}",
                requestId(),
                primary,
                primaryFailure != null
                        ? primaryFailure.getClass().getSimpleName()
                        : null,
                maxAttempts,
                fallbacks != null ? fallbacks.size() : 0
        );
        /*
         * If maxAttempts == 1, there is no opportunity for failover.
         */
        if (maxAttempts <= 1) {
            throw propagate(
                    primaryFailure,
                    lastFailure);
        }

        /*
         * Attempts after the primary are bounded by maxAttempts.
         *
         * Example:
         *   maxAttempts = 2
         *   attempt 1 = primary
         *   attempt 2 = first fallback
         */
        int fallbackAttempts = 0;

        for (Provider fallback : fallbacks) {

            /*
             * The configured fallback list may contain:
             * - null entries
             * - duplicate providers
             * - the primary provider
             */
            if (fallback == null
                    || attempted.contains(fallback)) {
                continue;
            }

            /*
             * Total attempts includes the primary attempt.
             */
            if (fallbackAttempts + 1 >= maxAttempts) {
                break;
            }
            log.info(
                    "FAILOVER_CANDIDATE requestId={} primary={} fallback={} fallbackAttempt={} maxAttempts={}",
                    requestId(),
                    primary,
                    fallback,
                    fallbackAttempts + 1,
                    maxAttempts
            );
            /*
             * Resolve and validate the fallback provider/model before
             * recording a failover attempt. This is important:
             *
             * An unavailable configured fallback is not an actual
             * provider execution attempt.
             */
            AIRequest fallbackRequest =
                    buildFallbackRequest(
                            request,
                            fallback);

            if (fallbackRequest == null) {

                log.warn(
                        "FAILOVER_CANDIDATE_REJECTED requestId={} primary={} fallback={}",
                        requestId(),
                        primary,
                        fallback
                );

                continue;
            }

            attempted.add(fallback);
            fallbackAttempts++;

            performanceLogger.failover(
                    requestId(),
                    primary.name(),
                    fallback.name(),
                    fallbackAttempts + 1);

            /*
             * We are now actually failing over to another provider.
             */
            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);

            routingAnalyticsService.recordFailoverAttempt();

            try {

                AIResponse response =
                        invoke(fallbackRequest, fallbackAttempts + 1);

                /*
                 * Failover succeeded.
                 */
                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_SUCCESS);

                routingAnalyticsService.recordFailoverSuccess();

                return response;

            } catch (Exception ex) {

                lastFailure = ex;

                recordProviderFailure(
                        fallbackRequest,
                        ex);

                if (!isRetryableFailure(ex)) {
                    log.info(
                            "FAILOVER_FALLBACK_NOT_RETRYABLE requestId={} provider={} failureType={}",
                            requestId(),
                            fallback,
                            ex.getClass().getSimpleName());
                    break;
                }

                /*
                 * Continue to the next configured fallback only for a
                 * transient/retryable provider failure.
                 */
            }
        }

        /*
         * We reached this point only after at least one actual
         * fallback execution failed.
         *
         * Therefore record failover failure exactly once.
         */
        if (fallbackAttempts > 0) {
            routingAnalyticsService.recordFailoverFailure();
        }

        /*
         * Preserve the original primary failure as the main exception.
         * The final fallback failure is retained as a suppressed exception.
         */
        throw propagate(
                primaryFailure,
                lastFailure);
    }

    private java.util.UUID requestId() {
        String value = MDC.get("requestId");
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            log.debug("Invalid requestId in MDC: {}", value);
            return null;
        }
    }

    private AIResponse invoke(AIRequest request, int attempt) {
        if (properties.getFailureInjection() != null
                && properties.getFailureInjection()
                .matches(request.getProvider(), request.getModel())) {
            String type = properties.getFailureInjection().getFailureType();
            throw new IllegalStateException(
                    "Controlled provider failure injection: " + type);
        }

        AIProvider provider =
                providerFactory.getProvider(request.getProvider());

        String previousAttempt = MDC.get("providerAttempt");
        MDC.put("providerAttempt", String.valueOf(attempt));
        try {
            AIResponse response = provider.chat(request);
            if (response != null) {
                response.setProvider(request.getProvider());
                response.setModel(request.getModel());
            }
            return response;
        } finally {
            if (previousAttempt == null) {
                MDC.remove("providerAttempt");
            } else {
                MDC.put("providerAttempt", previousAttempt);
            }
        }
    }

    private void recordProviderFailure(AIRequest request, Exception ex) {
        if (request == null
                || request.getProvider() == null
                || request.getModel() == null) {
            return;
        }

        if (routingHealthService != null) {
            routingHealthService.recordFailure(
                    new RoutingCandidate(
                            request.getProvider(),
                            request.getModel()),
                    ex.getClass().getSimpleName());
        }
    }
    private AIRequest buildFallbackRequest(
            AIRequest primaryRequest,
            Provider fallback) {

        try {

            providerModelRegistryService.requireProvider(fallback);

            String fallbackModelId =
                    defaultModel(fallback);

            var fallbackModel =
                    providerModelRegistryService.requireModel(
                            fallback,
                            fallbackModelId);

            return AIRequest.builder()
                    .provider(fallback)
                    .model(fallbackModel.modelId())
                    .prompt(primaryRequest.getPrompt())
                    .routingDecisionMetadata(primaryRequest.getRoutingDecisionMetadata())
                    .routingStrategy(primaryRequest.getRoutingStrategy())
                    .build();

        } catch (Exception ex) {

            log.warn(
                    "Configured fallback unavailable: primary={} fallback={} error={}",
                    primaryRequest.getProvider(),
                    fallback,
                    ex.getMessage());

            return null;
        }
    }


    private String defaultModel(Provider provider) {
        return providerFactory
                .getProvider(provider)
                .defaultModel();
    }

    /**
     * Only transient provider failures should trigger another provider call.
     * HTTP 4xx errors such as invalid requests or authentication failures are
     * not made better by failover. Network/timeout failures and 408/429/5xx
     * responses are considered retryable. Unknown provider runtime failures
     * remain retryable for backwards compatibility with provider adapters and
     * controlled failure injection tests.
     */
    private boolean isRetryableFailure(Throwable failure) {
        Throwable current = failure;

        while (current != null) {
            if (current instanceof ResourceAccessException) {
                return true;
            }

            if (current instanceof RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 408
                        || status == 429
                        || status >= 500;
            }

            current = current.getCause();
        }

        return true;
    }

    private RuntimeException propagate(
            Throwable primaryFailure,
            Throwable finalFailure) {

        Throwable failure =
                primaryFailure != null
                        ? primaryFailure
                        : finalFailure;

        if (failure instanceof RuntimeException runtimeException) {
            if (finalFailure != failure) {
                runtimeException.addSuppressed(finalFailure);
            }
            return runtimeException;
        }

        return new IllegalStateException(
                "Provider execution failed.",
                failure);
    }
}
