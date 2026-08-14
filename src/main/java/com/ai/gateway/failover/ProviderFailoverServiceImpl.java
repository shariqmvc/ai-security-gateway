package com.ai.gateway.failover;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public AIResponse execute(AIRequest request) {

        if (request == null || request.getProvider() == null) {
            throw new IllegalArgumentException(
                    "AI request and primary provider are required.");
        }

        if (!properties.isEnabled()) {
            return invoke(request);
        }

        int maxAttempts = Math.max(1, properties.getMaxAttempts());

        Set<Provider> attempted = new HashSet<>();
        Throwable primaryFailure = null;

        Provider primary = request.getProvider();
        AIRequest current = request;

        List<Provider> fallbacks = properties.fallbacksFor(primary);

        for (int attempt = 0; attempt < maxAttempts; attempt++) {

            Provider provider = current.getProvider();

            if (!attempted.add(provider)) {
                continue;
            }

            try {
                AIResponse response = invoke(current);

                if (attempt > 0) {
                    metricsService.increment(
                            MetricsConstants.ROUTING_FAILOVER_SUCCESS);
                    routingAnalyticsService.recordFailoverSuccess();

                    log.warn(
                            "Provider failover succeeded: primary={} fallback={} model={}",
                            primary,
                            provider,
                            current.getModel());
                }

                return response;

            } catch (Exception ex) {

                if (attempt == 0) {
                    primaryFailure = ex;
                } else {
                    routingAnalyticsService.recordFailoverFailure();
                }

                if (attempt + 1 >= maxAttempts) {
                    throw propagate(primaryFailure, ex);
                }

                log.warn(
                        "Provider execution failed: provider={} model={} attempt={} error={}",
                        provider,
                        current.getModel(),
                        attempt + 1,
                        ex.getMessage());

                AIRequest fallbackRequest =
                        nextFallbackRequest(
                                request,
                                fallbacks,
                                attempted);

                if (fallbackRequest == null) {
                    throw propagate(primaryFailure, ex);
                }

                metricsService.increment(
                        MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);

                routingAnalyticsService.recordFailoverAttempt();

                metricsService.incrementProviderRequest(
                        fallbackRequest.getProvider());

                current = fallbackRequest;
            }
        }

        throw propagate(
                primaryFailure,
                new IllegalStateException(
                        "Provider failover exhausted."));
    }

    private AIResponse invoke(AIRequest request) {
        AIProvider provider =
                providerFactory.getProvider(request.getProvider());

        return provider.chat(request);
    }

    private AIRequest nextFallbackRequest(
            AIRequest primaryRequest,
            List<Provider> fallbacks,
            Set<Provider> attempted) {

        for (Provider fallback : fallbacks) {

            if (fallback == null || attempted.contains(fallback)) {
                continue;
            }

            try {
                providerModelRegistryService.requireProvider(fallback);

                String fallbackModelId = defaultModel(fallback);

                var fallbackModel =
                        providerModelRegistryService.requireModel(
                                fallback,
                                fallbackModelId);

                return AIRequest.builder()
                        .provider(fallback)
                        .model(fallbackModel.modelId())
                        .prompt(primaryRequest.getPrompt())
                        .build();

            } catch (Exception ex) {

                log.warn(
                        "Configured fallback unavailable: primary={} fallback={} error={}",
                        primaryRequest.getProvider(),
                        fallback,
                        ex.getMessage());
            }
        }

        return null;
    }

    private String defaultModel(Provider provider) {
        return providerFactory
                .getProvider(provider)
                .defaultModel();
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
