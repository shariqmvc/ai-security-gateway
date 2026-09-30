package com.ai.gateway.core.failover;

import com.ai.gateway.config.ProviderRequestBudget;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.AIProviderFactory;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.core.provider.StreamingAIProvider;
import com.ai.gateway.core.metrics.GatewayMetricsService;
import com.ai.gateway.core.metrics.MetricsConstants;
import com.ai.gateway.core.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.health.RoutingHealthService;
import com.ai.gateway.core.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Streaming provider execution boundary with bounded, ordered failover.
 *
 * <p>Failover is only attempted before the first response delta has been
 * emitted. Once content has reached the caller, changing providers would
 * produce a discontinuous response stream and can corrupt the conversation.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingProviderFailoverServiceImpl
        implements StreamingProviderFailoverService {

    private final AIProviderFactory providerFactory;
    private final ProviderModelRegistryService providerModelRegistryService;
    private final FailoverProperties properties;
    private final GatewayMetricsService metricsService;
    private final RoutingAnalyticsService routingAnalyticsService;
    private final PerformanceLogger performanceLogger;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RoutingHealthService routingHealthService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ProviderCircuitBreaker providerCircuitBreaker;

    @Override
    public AIStreamResult stream(
            AIRequest request,
            Consumer<String> deltaConsumer) {

        if (request == null || request.getProvider() == null) {
            throw new IllegalArgumentException(
                    "AI request and primary provider are required.");
        }
        if (deltaConsumer == null) {
            throw new IllegalArgumentException("Delta consumer is required.");
        }

        ProviderRequestBudget.start(properties.getRequestTimeBudget());
        try {
            return execute(request, deltaConsumer);
        } finally {
            ProviderRequestBudget.clear();
        }
    }

    private AIStreamResult execute(
            AIRequest request,
            Consumer<String> deltaConsumer) {

        Provider primary = request.getProvider();
        Set<Provider> attempted = new HashSet<>();
        attempted.add(primary);

        Throwable primaryFailure = null;
        Throwable lastFailure = null;

        List<Provider> fallbacks = properties.isEnabled()
                ? properties.fallbacksFor(primary)
                : List.of();

        int maxAttempts = properties.isEnabled()
                ? Math.max(1, properties.getMaxAttempts())
                : 1;

        log.info(
                "STREAM_FAILOVER_PLAN requestId={} primary={} enabled={} maxAttempts={} configuredFallbacks={}",
                requestId(), primary, properties.isEnabled(), maxAttempts, fallbacks);

        if (!isCircuitOpen(primary, request.getModel())) {
            try {
                return invoke(request, deltaConsumer, 1);
            } catch (Exception ex) {
                primaryFailure = ex;
                lastFailure = ex;
                recordFailure(request, ex);

                /*
                 * Never switch providers after the primary has emitted even
                 * one delta. The caller already owns that response prefix.
                 */
                if (ex instanceof StreamingProviderFailureException
                        && ((StreamingProviderFailureException) ex).partialOutputEmitted()) {
                    log.info(
                            "STREAM_FAILOVER_STOP_PRIMARY_PARTIAL_OUTPUT requestId={} provider={}",
                            requestId(),
                            request.getProvider());
                    throw propagate(primary, primaryFailure, lastFailure);
                }

                if (!ProviderFailureClassifier.isRetryable(ex)
                        || maxAttempts <= 1) {
                    throw propagate(primary, primaryFailure, lastFailure);
                }
            }
        } else {
            long retryAfterMs =
                    providerCircuitBreaker == null
                            ? 0L
                            : providerCircuitBreaker.retryAfterMs(
                                    request.getProvider(), request.getModel());

            primaryFailure =
                    new ProviderCircuitOpenException(
                            request.getProvider(),
                            request.getModel(),
                            retryAfterMs);
            lastFailure = primaryFailure;

            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_CIRCUIT_OPEN);

            log.info(
                    "STREAM_FAILOVER_PRIMARY_CIRCUIT_OPEN requestId={} provider={} model={} retryAfterMs={}",
                    requestId(),
                    request.getProvider(),
                    request.getModel(),
                    retryAfterMs);
        }

        int fallbackAttempts = 0;

        for (Provider fallback : fallbacks) {
            if (fallback == null || attempted.contains(fallback)) {
                continue;
            }

            if (fallbackAttempts + 1 >= maxAttempts) {
                break;
            }

            long remainingBudgetMs = ProviderRequestBudget.remainingMillis();
            long minimumFallbackBudgetMs =
                    properties.getMinimumFallbackBudget() == null
                            ? 0L
                            : Math.max(
                                    0L,
                                    properties.getMinimumFallbackBudget().toMillis());

            if (ProviderRequestBudget.isActive()
                    && remainingBudgetMs < minimumFallbackBudgetMs) {

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);

                log.info(
                        "STREAM_FAILOVER_BUDGET_EXHAUSTED requestId={} remainingBudgetMs={} minimumFallbackBudgetMs={}",
                        requestId(),
                        remainingBudgetMs,
                        minimumFallbackBudgetMs);
                break;
            }

            AIRequest fallbackRequest =
                    buildFallbackRequest(request, fallback);

            if (fallbackRequest == null) {
                continue;
            }

            RoutingCandidate candidate =
                    new RoutingCandidate(
                            fallbackRequest.getProvider(),
                            fallbackRequest.getModel());

            if (routingHealthService != null
                    && !routingHealthService.isHealthyForRouting(candidate)) {
                log.info(
                        "STREAM_FAILOVER_CANDIDATE_UNHEALTHY requestId={} provider={} model={}",
                        requestId(),
                        fallbackRequest.getProvider(),
                        fallbackRequest.getModel());
                continue;
            }

            if (isCircuitOpen(
                    fallbackRequest.getProvider(),
                    fallbackRequest.getModel())) {

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_CIRCUIT_OPEN);

                log.info(
                        "STREAM_FAILOVER_CANDIDATE_CIRCUIT_OPEN requestId={} provider={} model={} retryAfterMs={}",
                        requestId(),
                        fallbackRequest.getProvider(),
                        fallbackRequest.getModel(),
                        providerCircuitBreaker == null
                                ? 0L
                                : providerCircuitBreaker.retryAfterMs(
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
            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);
            routingAnalyticsService.recordFailoverAttempt();

            try {
                AIStreamResult response =
                        invoke(
                                fallbackRequest,
                                deltaConsumer,
                                fallbackAttempts + 1);

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_SUCCESS);
                routingAnalyticsService.recordFailoverSuccess();
                return response;

            } catch (Exception ex) {
                lastFailure = ex;
                recordFailure(fallbackRequest, ex);

                if (ex instanceof StreamingProviderFailureException
                        && ((StreamingProviderFailureException) ex).partialOutputEmitted()) {
                    log.info(
                            "STREAM_FAILOVER_STOP_PARTIAL_OUTPUT requestId={} provider={}",
                            requestId(),
                            fallbackRequest.getProvider());
                    break;
                }

                if (!ProviderFailureClassifier.isRetryable(ex)) {
                    log.info(
                            "STREAM_FAILOVER_FALLBACK_NOT_RETRYABLE requestId={} provider={} failureType={}",
                            requestId(),
                            fallback,
                            ProviderFailureClassifier.classify(ex));
                    break;
                }
            }
        }

        if (fallbackAttempts > 0) {
            routingAnalyticsService.recordFailoverFailure();
        }

        throw propagate(primary, primaryFailure, lastFailure);
    }

    private AIStreamResult invoke(
            AIRequest request,
            Consumer<String> deltaConsumer,
            int attempt) {

        ensureRequestBudgetAvailable(
                request.getProvider(), request.getModel(), attempt);

        AIProviderFactory factory = providerFactory;
        var provider = factory.getProvider(request.getProvider());

        if (!(provider instanceof StreamingAIProvider streamingProvider)) {
            throw new UnsupportedOperationException(
                    "Streaming is not supported by provider "
                            + request.getProvider()
                            + " / "
                            + request.getModel());
        }

        String previousAttempt = MDC.get("providerAttempt");
        MDC.put("providerAttempt", String.valueOf(attempt));

        long startedAtNanos = System.nanoTime();
        final boolean[] emitted = {false};

        try {
            AIStreamResult result =
                    streamingProvider.stream(
                            request,
                            delta -> {
                                if (delta != null && !delta.isEmpty()) {
                                    emitted[0] = true;
                                }
                                deltaConsumer.accept(delta);
                            });

            ensureRequestBudgetAvailable(
                    request.getProvider(), request.getModel(), attempt);

            long latencyMs = Math.max(
                    0L,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                            System.nanoTime() - startedAtNanos));

            recordSuccess(
                    request,
                    result == null || result.getLatencyMs() == null
                            ? latencyMs
                            : result.getLatencyMs());

            if (result == null) {
                return null;
            }

            /*
             * AIStreamResult is immutable (@Value), so preserve the provider
             * and model selected by the attempt by creating a new result.
             */
            return AIStreamResult.builder()
                    .response(result.getResponse())
                    .provider(request.getProvider())
                    .model(request.getModel())
                    .inputTokens(result.getInputTokens())
                    .outputTokens(result.getOutputTokens())
                    .totalTokens(result.getTotalTokens())
                    .latencyMs(result.getLatencyMs())
                    .finishReason(result.getFinishReason())
                    .build();

        } catch (Exception ex) {
            /*
             * A provider may have emitted partial output and then failed.
             * Once content has been emitted, failover is unsafe because the
             * caller already has a prefix from this provider.
             *
             * Preserve that state in the exception so the outer executor can
             * terminate rather than invoking another provider.
             */
            throw new StreamingProviderFailureException(ex, emitted[0]);
        } finally {
            if (previousAttempt == null) {
                MDC.remove("providerAttempt");
            } else {
                MDC.put("providerAttempt", previousAttempt);
            }
        }
    }

    private AIRequest buildFallbackRequest(
            AIRequest primaryRequest,
            Provider fallback) {
        try {
            providerModelRegistryService.requireProvider(fallback);
            String fallbackModelId =
                    providerFactory.getProvider(fallback).defaultModel();

            var fallbackModel =
                    providerModelRegistryService.requireModel(
                            fallback, fallbackModelId);

            return AIRequest.builder()
                    .provider(fallback)
                    .model(fallbackModel.modelId())
                    .prompt(primaryRequest.getPrompt())
                    .billingMode(primaryRequest.getBillingMode())
                    .maximumRequestCost(primaryRequest.getMaximumRequestCost())
                    .personalAccountId(primaryRequest.getPersonalAccountId())
                    .media(primaryRequest.getMedia())
                    .fileProcessingMode(primaryRequest.getFileProcessingMode())
                    .routingDecisionMetadata(
                            primaryRequest.getRoutingDecisionMetadata())
                    .routingStrategy(primaryRequest.getRoutingStrategy())
                    .build();
        } catch (Exception ex) {
            log.warn(
                    "Configured streaming fallback unavailable primary={} fallback={} error={}",
                    primaryRequest.getProvider(),
                    fallback,
                    ex.getMessage());
            return null;
        }
    }

    private void recordSuccess(AIRequest request, long latencyMs) {
        if (routingHealthService != null) {
            routingHealthService.recordSuccess(
                    new RoutingCandidate(
                            request.getProvider(),
                            request.getModel()),
                    latencyMs);
        }
        if (providerCircuitBreaker != null) {
            providerCircuitBreaker.recordSuccess(
                    request.getProvider(),
                    request.getModel());
        }
    }

    private void recordFailure(AIRequest request, Throwable failure) {
        if (request == null
                || request.getProvider() == null
                || request.getModel() == null) {
            return;
        }

        if (ProviderFailureClassifier.classify(failure)
                == ProviderFailureCategory.MEDIA_INPUT) {
            return;
        }

        if (routingHealthService != null) {
            routingHealthService.recordFailure(
                    new RoutingCandidate(
                            request.getProvider(),
                            request.getModel()),
                    ProviderFailureClassifier.classify(failure).name());
        }

        if (providerCircuitBreaker != null
                && ProviderFailureClassifier.isRetryable(failure)) {

            ProviderFailureCategory category =
                    ProviderFailureClassifier.classify(failure);

            long openDurationMs =
                    ProviderFailureClassifier.retryAfter(failure)
                            .map(java.time.Duration::toMillis)
                            .orElse(0L);

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

    private void ensureRequestBudgetAvailable(
            Provider provider,
            String model,
            int attempt) {
        if (!ProviderRequestBudget.isActive()) {
            return;
        }

        long remainingMs = ProviderRequestBudget.remainingMillis();

        if (remainingMs <= 0L) {
            metricsService.increment(
                    MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);
            throw new com.ai.gateway.config.ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted for "
                            + provider + "/" + model
                            + " before attempt " + attempt + ".");
        }
    }

    private boolean isCircuitOpen(Provider provider, String model) {
        return providerCircuitBreaker != null
                && !providerCircuitBreaker.allowRequest(provider, model);
    }

    private UUID requestId() {
        String value = MDC.get("requestId");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private RuntimeException propagate(
            Provider provider,
            Throwable primaryFailure,
            Throwable finalFailure) {
        Throwable failure =
                primaryFailure != null
                        ? primaryFailure
                        : finalFailure;

        if (failure instanceof StreamingProviderFailureException wrapper
                && wrapper.getCause() != null) {
            failure = wrapper.getCause();
        }

        if (failure instanceof org.springframework.web.client.RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            if (status == 401 || status == 403) {
                return new ProviderAuthenticationException(
                        provider,
                        responseException);
            }
        }

        if (failure instanceof RuntimeException runtimeException) {
            if (finalFailure != null
                    && finalFailure != primaryFailure
                    && finalFailure != failure) {
                runtimeException.addSuppressed(finalFailure);
            }
            return runtimeException;
        }

        return new IllegalStateException(
                "Provider streaming execution failed.",
                failure);
    }

    public static class StreamingProviderFailureException
            extends RuntimeException {
        private final boolean partialOutputEmitted;

        public StreamingProviderFailureException(
                Throwable cause,
                boolean partialOutputEmitted) {
            super(cause);
            this.partialOutputEmitted = partialOutputEmitted;
        }

        public boolean partialOutputEmitted() {
            return partialOutputEmitted;
        }
    }
}
