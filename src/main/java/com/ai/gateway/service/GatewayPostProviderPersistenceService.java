package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.business.cost.service.CostService;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.intelligence.RoutingRuntimeSignalService;
import com.ai.gateway.core.routing.telemetry.RoutingTelemetryEvent;
import com.ai.gateway.core.routing.telemetry.RoutingTelemetryService;
import com.ai.gateway.business.routing.health.RoutingOutcomeService;
import com.ai.gateway.core.metrics.GatewayMetricsService;
import com.ai.gateway.enums.AuditStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Moves non-critical post-provider persistence and observability off the HTTP
 * response path. Governance enforcement (token quota and budget) remains
 * synchronous in GatewayServiceImpl.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayPostProviderPersistenceService {

    private final RoutingRuntimeSignalService routingRuntimeSignalService;
    private final RoutingOutcomeService routingOutcomeService;
    private final RoutingTelemetryService routingTelemetryService;
    private final TokenUsageService tokenUsageService;
    private final CostService costService;
    private final AuditService auditService;
    private final GatewayMetricsService metricsService;

    @Async("gatewayAsyncExecutor")
    public void persistSuccess(
            UUID requestId,
            AuthenticationContext auth,
            AIRequest request,
            AIResponse response,
            RoutingDecision routingDecision,
            long providerLatency,
            String maskedPrompt,
            long totalLatency) {

        try {
            String endpointId = response != null && response.getEndpointId() != null
                    ? response.getEndpointId()
                    : request.getEndpointId();

            RoutingCandidate candidate =
                    new RoutingCandidate(
                            request.getProvider(),
                            request.getModel(),
                            endpointId);

            routingRuntimeSignalService.recordSuccess(candidate, providerLatency);

            if (routingOutcomeService != null) {
                routingOutcomeService.recordSuccess(
                        requestId,
                        auth,
                        request,
                        routingDecision,
                        providerLatency);
            }

            if (response != null) {
                var usage = response.getUsage();
                routingTelemetryService.record(
                        new RoutingTelemetryEvent(
                                requestId,
                                request.getProvider().name(),
                                request.getModel(),
                                endpointId,
                                true,
                                providerLatency,
                                usage == null ? null : usage.getInputTokens(),
                                usage == null ? null : usage.getOutputTokens(),
                                usage == null ? null : usage.getTotalTokens(),
                                usage == null ? null : usage.getReasoningTokens(),
                                response.getFinishReason(),
                                null,
                                Instant.now()));
            }

            if (response != null && response.getUsage() != null
                    && (auth == null || !auth.isPersonalPrincipal())) {
                // These persistence services are Business tenant-schema scoped.
                // Personal billing/usage must not attempt a null tenant lookup.
                tokenUsageService.save(requestId, request, response);
                costService.persist(requestId, auth, request, response);
            }

            if (auth == null || !auth.isPersonalPrincipal()) {
                auditService.save(
                        requestId,
                        maskedPrompt,
                        response == null ? null : response.getResponse(),
                        totalLatency,
                        request.getModel(),
                        request.getProvider().name(),
                        AuditStatus.SUCCESS);
            }

            metricsService.addLatency(totalLatency);
            metricsService.increment(com.ai.gateway.core.metrics.MetricsConstants.SUCCESSFUL_REQUESTS);
        } catch (Exception ex) {
            log.error("Post-provider success persistence failed: requestId={}", requestId, ex);
        }
    }

    @Async("gatewayAsyncExecutor")
    public void persistFailure(
            UUID requestId,
            AuthenticationContext auth,
            AIRequest request,
            RoutingDecision routingDecision,
            long providerLatency,
            String maskedPrompt,
            long totalLatency,
            boolean providerFailed,
            String failureCategory) {

        try {
            if (providerFailed
                    && request != null
                    && request.getProvider() != null
                    && request.getModel() != null) {

                RoutingCandidate candidate =
                        new RoutingCandidate(
                                request.getProvider(),
                                request.getModel(),
                                request.getEndpointId());

                String category = failureCategory == null
                        ? "PROVIDER_FAILURE"
                        : failureCategory;

                routingRuntimeSignalService.recordFailure(candidate, category);

                routingTelemetryService.record(
                        new RoutingTelemetryEvent(
                                requestId,
                                request.getProvider().name(),
                                request.getModel(),
                                request.getEndpointId(),
                                false,
                                providerLatency,
                                null,
                                null,
                                null,
                                null,
                                null,
                                category,
                                Instant.now()));

                if (routingOutcomeService != null) {
                    routingOutcomeService.recordFailure(
                            requestId,
                            auth,
                            request,
                            routingDecision,
                            providerLatency,
                            new IllegalStateException(category));
                }
            }

            if (auth == null || !auth.isPersonalPrincipal()) {
                auditService.save(
                        requestId,
                        maskedPrompt,
                        null,
                        totalLatency,
                        request == null ? null : request.getModel(),
                        request == null || request.getProvider() == null
                                ? null
                                : request.getProvider().name(),
                        AuditStatus.FAILED);
            }

            metricsService.addLatency(totalLatency);
            metricsService.increment(com.ai.gateway.core.metrics.MetricsConstants.FAILED_REQUESTS);
        } catch (Exception ex) {
            log.error("Post-provider failure persistence failed: requestId={}", requestId, ex);
        }
    }
}
