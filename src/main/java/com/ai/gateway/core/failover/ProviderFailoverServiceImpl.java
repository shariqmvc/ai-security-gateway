package com.ai.gateway.core.failover;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.config.ProviderRequestBudget;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.metrics.GatewayMetricsService;
import com.ai.gateway.core.metrics.MetricsConstants;
import com.ai.gateway.core.observability.PerformanceLogger;
import org.slf4j.MDC;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIProviderFactory;
import com.ai.gateway.core.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.health.RoutingHealthService;
import com.ai.gateway.core.routing.registry.ProviderModelRegistryService;
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

    /**
     * Local, low-latency circuit breaker. Optional injection preserves the
     * lightweight unit-test construction path while production receives the
     * Spring-managed breaker.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProviderCircuitBreaker providerCircuitBreaker;

    @Override
    public AIResponse execute(AIRequest request) {
        ProviderRequestBudget.start(properties.getRequestTimeBudget());
        try {
            return executeWithinBudget(request);
        } finally {
            ProviderRequestBudget.clear();
        }
    }

    private AIResponse executeWithinBudget(AIRequest request) {

        if (request == null || request.getProvider() == null) {
            throw new IllegalArgumentException(
                    "AI request and primary provider are required.");
        }

        /*
         * Failover is disabled: execute the primary provider directly.
         * No failover metrics or analytics are recorded.
         */
        if (!properties.isEnabled()) {
            try {
                return invoke(request, 1);
            } catch (Exception ex) {
                throw normalizeNonRetryableProviderFailure(request.getProvider(), ex);
            }
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

        /*
         * Fast-fail a provider/model that is already known to be unhealthy.
         * Subsequent requests should not pay the same provider timeout again.
         */
        if (!isCircuitOpen(request.getProvider(), request.getModel())) {

            try {
                AIResponse response = invoke(request, 1);
                recordProviderSuccess(request);
                return response;

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
                    throw propagate(request.getProvider(), primaryFailure, lastFailure);
                }
            }

        } else {

            long retryAfterMs =
                    circuitRetryAfterMs(
                            request.getProvider(),
                            request.getModel());

            primaryFailure =
                    new ProviderCircuitOpenException(
                            request.getProvider(),
                            request.getModel(),
                            retryAfterMs);

            lastFailure = primaryFailure;

            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_CIRCUIT_OPEN);

            log.info(
                    "FAILOVER_PRIMARY_CIRCUIT_OPEN requestId={} provider={} model={} retryAfterMs={}",
                    requestId(),
                    request.getProvider(),
                    request.getModel(),
                    retryAfterMs);
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
                    request.getProvider(),
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

            long remainingBudgetMs =
                    ProviderRequestBudget.remainingMillis();

            long minimumFallbackBudgetMs =
                    properties.getMinimumFallbackBudget() == null
                            ? 0L
                            : Math.max(0L, properties.getMinimumFallbackBudget().toMillis());

            if (ProviderRequestBudget.isActive()
                    && remainingBudgetMs < minimumFallbackBudgetMs) {

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);

                log.info(
                        "FAILOVER_BUDGET_EXHAUSTED requestId={} remainingBudgetMs={} minimumFallbackBudgetMs={}",
                        requestId(),
                        remainingBudgetMs,
                        minimumFallbackBudgetMs);

                break;
            }

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

            if (isCircuitOpen(
                    fallbackRequest.getProvider(),
                    fallbackRequest.getModel())) {

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_CIRCUIT_OPEN);

                log.info(
                        "FAILOVER_CANDIDATE_CIRCUIT_OPEN requestId={} provider={} model={} retryAfterMs={}",
                        requestId(),
                        fallbackRequest.getProvider(),
                        fallbackRequest.getModel(),
                        circuitRetryAfterMs(
                                fallbackRequest.getProvider(),
                                fallbackRequest.getModel()));

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

                recordProviderSuccess(fallbackRequest);

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
                request.getProvider(),
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
        ensureRequestBudgetAvailable(
                request.getProvider(),
                request.getModel(),
                attempt);

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

            /*
             * A provider may successfully return after the gateway deadline
             * has already expired (for example, because the underlying HTTP
             * client uses a read/inactivity timeout rather than a total
             * request timeout). Never return such a response as a successful
             * gateway response and never start another provider attempt.
             */
            ensureRequestBudgetAvailable(
                    request.getProvider(),
                    request.getModel(),
                    attempt);

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

    private void ensureRequestBudgetAvailable(
            Provider provider,
            String model,
            int attempt) {

        if (!ProviderRequestBudget.isActive()) {
            return;
        }

        long remainingMs =
                ProviderRequestBudget.remainingMillis();

        if (remainingMs <= 0L) {
            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);

            log.info(
                    "REQUEST_DEADLINE_EXCEEDED requestId={} provider={} model={} attempt={}",
                    requestId(),
                    provider,
                    model,
                    attempt);

            throw new com.ai.gateway.config.ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted for "
                            + provider + "/" + model
                            + " before attempt " + attempt + ".");
        }
    }

    private void recordProviderFailure(AIRequest request, Exception ex) {
        if (request == null
                || request.getProvider() == null
                || request.getModel() == null) {
            return;
        }

        /*
         * Media acquisition/validation failures originate from caller-supplied
         * input, not from provider availability. Do not mark the provider
         * unhealthy and do not update its circuit state.
         */
        if (ProviderFailureClassifier.classify(ex)
                == ProviderFailureCategory.MEDIA_INPUT) {
            return;
        }

        if (routingHealthService != null) {
            routingHealthService.recordFailure(
                    new RoutingCandidate(
                            request.getProvider(),
                            request.getModel()),
                    ex.getClass().getSimpleName());
        }

        recordProviderCircuitFailure(request, ex);
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
                    .media(primaryRequest.getMedia())
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
     * The same normalized classification is also used by the local circuit
     * breaker so retry and health behavior remain consistent.
     */
    private boolean isRetryableFailure(Throwable failure) {
        return ProviderFailureClassifier.isRetryable(failure);
    }

    private void recordProviderSuccess(AIRequest request) {
        if (providerCircuitBreaker == null
                || request == null
                || request.getProvider() == null
                || request.getModel() == null
                || request.getModel().isBlank()) {
            return;
        }

        providerCircuitBreaker.recordSuccess(
                request.getProvider(),
                request.getModel());
    }

    private void recordProviderCircuitFailure(
            AIRequest request,
            Throwable failure) {

        if (providerCircuitBreaker == null
                || request == null
                || request.getProvider() == null
                || request.getModel() == null
                || request.getModel().isBlank()) {
            return;
        }

        ProviderFailureCategory category =
                ProviderFailureClassifier.classify(failure);

        if (ProviderFailureClassifier.isRetryable(failure)) {
            long openDurationMs =
                    ProviderFailureClassifier.retryAfter(failure)
                            .map(java.time.Duration::toMillis)
                            .orElseGet(
                                    () -> {
                                        /*
                                         * Use the configured category duration
                                         * when the provider did not supply a
                                         * Retry-After header.
                                         */
                                        return 0L;
                                    });

            if (openDurationMs > 0L) {
                providerCircuitBreaker.recordFailure(
                        request.getProvider(),
                        request.getModel(),
                        category,
                        openDurationMs);
            } else {
                providerCircuitBreaker.recordFailure(
                        request.getProvider(),
                        request.getModel(),
                        category);
            }
        }
    }

    private boolean isCircuitOpen(Provider provider, String model) {
        return providerCircuitBreaker != null
                && !providerCircuitBreaker.allowRequest(provider, model);
    }

    private long circuitRetryAfterMs(Provider provider, String model) {
        return providerCircuitBreaker == null
                ? 0L
                : providerCircuitBreaker.retryAfterMs(provider, model);
    }

    private RuntimeException propagate(
            Provider provider,
            Throwable primaryFailure,
            Throwable finalFailure) {

        Throwable failure =
                primaryFailure != null
                        ? primaryFailure
                        : finalFailure;

        org.springframework.web.client.RestClientResponseException
                authenticationFailure = findAuthenticationFailure(failure);

        if (authenticationFailure != null) {
            return new ProviderAuthenticationException(
                    provider,
                    authenticationFailure);
        }

        if (failure instanceof RuntimeException runtimeException) {
            if (finalFailure != null && finalFailure != failure) {
                runtimeException.addSuppressed(finalFailure);
            }
            return runtimeException;
        }

        return new IllegalStateException(
                "Provider execution failed.",
                failure);
    }

    private RuntimeException normalizeNonRetryableProviderFailure(
            Provider provider, Throwable failure) {

        org.springframework.web.client.RestClientResponseException
                authenticationFailure = findAuthenticationFailure(failure);

        if (authenticationFailure != null) {
            return new ProviderAuthenticationException(
                    provider,
                    authenticationFailure);
        }

        return failure instanceof RuntimeException runtimeException
                ? runtimeException
                : new IllegalStateException(
                        "Provider execution failed.",
                        failure);
    }

    private org.springframework.web.client.RestClientResponseException
    findAuthenticationFailure(Throwable failure) {

        Throwable current = failure;

        while (current != null) {
            if (current instanceof org.springframework.web.client.RestClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                if (status == 401 || status == 403) {
                    return responseException;
                }
            }

            current = current.getCause();
        }

        return null;
    }


}
