package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.health.RoutingOutcomeService;
import com.ai.gateway.routing.intelligence.RoutingRuntimeSignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
            RoutingCandidate candidate =
                    new RoutingCandidate(request.getProvider(), request.getModel());

            routingRuntimeSignalService.recordSuccess(candidate, providerLatency);

            if (routingOutcomeService != null) {
                routingOutcomeService.recordSuccess(
                        requestId,
                        auth,
                        request,
                        routingDecision,
                        providerLatency);
            }

            if (response != null && response.getUsage() != null) {
                tokenUsageService.save(requestId, request, response);
                costService.persist(requestId, auth, request, response);
            }

            auditService.save(
                    requestId,
                    maskedPrompt,
                    response == null ? null : response.getResponse(),
                    totalLatency,
                    request.getModel(),
                    request.getProvider().name(),
                    AuditStatus.SUCCESS);

            metricsService.addLatency(totalLatency);
            metricsService.increment(com.ai.gateway.metrics.MetricsConstants.SUCCESSFUL_REQUESTS);
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
                        new RoutingCandidate(request.getProvider(), request.getModel());
                routingRuntimeSignalService.recordFailure(
                        candidate,
                        failureCategory == null ? "PROVIDER_FAILURE" : failureCategory);

                if (routingOutcomeService != null) {
                    routingOutcomeService.recordFailure(
                            requestId,
                            auth,
                            request,
                            routingDecision,
                            providerLatency,
                            new IllegalStateException(
                                    failureCategory == null
                                            ? "Provider execution failed"
                                            : failureCategory));
                }
            }

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

            metricsService.addLatency(totalLatency);
            metricsService.increment(com.ai.gateway.metrics.MetricsConstants.FAILED_REQUESTS);
        } catch (Exception ex) {
            log.error("Post-provider failure persistence failed: requestId={}", requestId, ex);
        }
    }
}
