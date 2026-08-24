package com.ai.gateway.service.impl;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.*;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.mapper.ProviderFeatureMapper;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.failover.ProviderFailoverService;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.governance.service.GovernanceGuardrailService;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.observability.PerformanceLogger;
import com.ai.gateway.observability.RequestCorrelationFilter;
import org.slf4j.MDC;
import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.rag.augmentation.RagAugmentationResult;
import com.ai.gateway.rag.augmentation.RagAugmentationService;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingService;
import com.ai.gateway.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import com.ai.gateway.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {

    private final PIIDetectionService piiDetectionService;

    private final TokenVaultService tokenVaultService;

    private final RestoreService restoreService;

    private final PromptFireWallService firewallService;

    private final PolicyEngineService policyEngineService;

    private final GatewayMetricsService metricsService;

    private final EntitlementService entitlementService;

    private final RoutingService routingService;

    private final RoutingAnalyticsService routingAnalyticsService;

    private final ProviderFailoverService providerFailoverService;

    private final PerformanceLogger performanceLogger;

    private final GatewayPostProviderPersistenceService postProviderPersistenceService;

    private final GovernanceGuardrailService governanceGuardrailService;

    private final RagAugmentationService ragAugmentationService;

    @Override
    @RequiresFeature(Feature.CHAT)
    public ChatResponse process(ChatRequest request) {

        metricsService.increment(MetricsConstants.TOTAL_REQUESTS);

        UUID requestId = resolveRequestId();

        long start = System.nanoTime();
        performanceLogger.requestStart(requestId, "/api/chat");

        String originalPrompt = request.getPrompt();

        String maskedPrompt = originalPrompt;



        long stageStart = System.nanoTime();
        AuthenticationContext auth = getAuthenticationContext();
        performanceLogger.stage("AUTHENTICATION", requestId, elapsedMs(stageStart), "SUCCESS");
        AIRequest aiRequest = null;
        boolean providerInvocationStarted = false;
        boolean providerInvocationSucceeded = false;
        long providerInvocationStart = 0L;

        try {


            stageStart = System.nanoTime();
            if (request.isExtensiveResearch()) {
                entitlementService.validateFeature(
                        auth.getTenantId(),
                        Feature.EXTENSIVE_RESEARCH);
            }
            performanceLogger.stage("ENTITLEMENT", requestId, elapsedMs(stageStart), "SUCCESS");

            stageStart = System.nanoTime();
            // -------------------------------
            // Prompt Firewall
            // Policy Engine
            // -------------------------------
            validateRequest(originalPrompt);
            performanceLogger.stage("FIREWALL_POLICY", requestId, elapsedMs(stageStart), "SUCCESS");



            stageStart = System.nanoTime();
            MaskingResult maskingResult =
                    maskPrompt(requestId, originalPrompt);

            maskedPrompt =
                    maskingResult.getMaskedPrompt();
            performanceLogger.stage("PII_MASKING_AND_TOKEN_VAULT", requestId, elapsedMs(stageStart), "SUCCESS");

            stageStart = System.nanoTime();
            RagAugmentationResult ragResult =
                    ragAugmentationService.augment(
                            auth.getTenantId(),
                            maskedPrompt,
                            request.getRag());
            String providerPrompt = ragResult.getAugmentedPrompt();
            performanceLogger.stage(
                    "RAG_AUGMENTATION",
                    requestId,
                    elapsedMs(stageStart),
                    request.getRag() != null && request.getRag().isEnabled()
                            ? "ENABLED"
                            : "DISABLED");

            stageStart = System.nanoTime();
            aiRequest =
                    buildAIRequest(
                            requestId,
                            request,
                            auth,
                            providerPrompt);

            performanceLogger.stage("ROUTING", requestId, elapsedMs(stageStart), "SUCCESS");

            stageStart = System.nanoTime();
            Feature feature =
                    ProviderFeatureMapper.toFeature(
                            aiRequest.getProvider());

            validateFeature(
                    auth,
                    feature);
            performanceLogger.stage("PROVIDER_ENTITLEMENT", requestId, elapsedMs(stageStart), "SUCCESS");
            // -------------------------------
            // Provider Invocation
            // -------------------------------

            providerInvocationStarted = true;
            providerInvocationStart = System.nanoTime();

            AIResponse aiResponse =
                    invokeProvider(aiRequest);

            if (aiResponse != null && aiResponse.getProvider() != null) {
                // Failover may have changed the actual execution target.
                // Carry that target forward so usage, cost, audit and routing
                // health are attributed to the provider that actually ran.
                aiRequest.setProvider(aiResponse.getProvider());
                if (aiResponse.getModel() != null && !aiResponse.getModel().isBlank()) {
                    aiRequest.setModel(aiResponse.getModel());
                }
            }

            providerInvocationSucceeded = true;

            long providerLatency = elapsedMs(providerInvocationStart);
            performanceLogger.stage("PROVIDER_EXECUTION", requestId, providerLatency, "SUCCESS");

            // Governance enforcement remains synchronous. Token quota and
            // budget controls therefore cannot be bypassed by the async path.
            stageStart = System.nanoTime();
            enforcePostProviderGuardrails(
                    requestId,
                    auth,
                    aiRequest,
                    aiResponse);
            performanceLogger.stage(
                    "GOVERNANCE_GUARDRAILS",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");

            stageStart = System.nanoTime();
            String restored =
                    restoreResponse(
                            requestId,
                            aiResponse);
            performanceLogger.stage(
                    "RESPONSE_RESTORE",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");

            long latency = elapsedMs(start);
            long gatewayOverhead = Math.max(0L, latency - providerLatency);

            RoutingDecision routingDecision =
                    new RoutingDecision(
                            aiRequest.getProvider(),
                            aiRequest.getModel(),
                            aiRequest.getRoutingStrategy(),
                            aiRequest.getRoutingDecisionMetadata());

            postProviderPersistenceService.persistSuccess(
                    requestId,
                    auth,
                    aiRequest,
                    aiResponse,
                    routingDecision,
                    providerLatency,
                    maskedPrompt,
                    latency);

            performanceLogger.stage(
                    "POST_PROVIDER_PERSISTENCE_ASYNC",
                    requestId,
                    0L,
                    "QUEUED");
            performanceLogger.requestCompleted(
                    requestId,
                    latency,
                    aiRequest.getProvider().name(),
                    aiRequest.getModel(),
                    "SUCCESS",
                    providerLatency,
                    gatewayOverhead);

            return ChatResponse.builder()
                    .requestId(requestId)
                    .response(restored)
                    .rag(buildRagMetadata(request.getRag(), ragResult))
                    .build();

        } catch (Exception ex) {

            long latency = elapsedMs(start);
            long providerLatency = providerInvocationStarted
                    ? elapsedMs(providerInvocationStart)
                    : 0L;
            long gatewayOverhead = Math.max(0L, latency - providerLatency);

            RoutingDecision routingDecision = aiRequest == null
                    ? null
                    : new RoutingDecision(
                            aiRequest.getProvider(),
                            aiRequest.getModel(),
                            aiRequest.getRoutingStrategy(),
                            aiRequest.getRoutingDecisionMetadata());

            postProviderPersistenceService.persistFailure(
                    requestId,
                    auth,
                    aiRequest,
                    routingDecision,
                    providerLatency,
                    maskedPrompt,
                    latency,
                    providerInvocationStarted && !providerInvocationSucceeded,
                    ex.getClass().getSimpleName());

            performanceLogger.stage(
                    "POST_PROVIDER_PERSISTENCE_ASYNC",
                    requestId,
                    0L,
                    "QUEUED");
            performanceLogger.requestCompleted(
                    requestId,
                    latency,
                    aiRequest != null && aiRequest.getProvider() != null
                            ? aiRequest.getProvider().name()
                            : null,
                    aiRequest != null ? aiRequest.getModel() : null,
                    "FAILED",
                    providerLatency,
                    gatewayOverhead);

            throw ex;
        }

    }

    private UUID resolveRequestId() {
        String requestId = MDC.get(RequestCorrelationFilter.REQUEST_ID);
        if (requestId != null) {
            try {
                return UUID.fromString(requestId);
            } catch (IllegalArgumentException ignored) {
                // Fall through to a new UUID. The correlation filter normally
                // guarantees a valid UUID, but the service remains safe when
                // invoked directly from tests or non-HTTP callers.
            }
        }
        return UUID.randomUUID();
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Retrieves AuthenticationContext created by AuthenticationFilter.
     */
    private AuthenticationContext getAuthenticationContext() {

        HttpServletRequest request =
                ((ServletRequestAttributes)
                        RequestContextHolder
                                .currentRequestAttributes())
                        .getRequest();

        AuthenticationContext context =
                (AuthenticationContext)
                        request.getAttribute(
                                AuthenticationConstants.AUTH_CONTEXT);

        if (context == null) {

            throw new IllegalStateException(
                    "AuthenticationContext not found.");

        }

        return context;

    }

    private void enforcePostProviderGuardrails(
            UUID requestId,
            AuthenticationContext auth,
            AIRequest request,
            AIResponse response) {

        governanceGuardrailService.enforce(
                requestId,
                auth,
                request,
                response);
    }

    private void validateRequest(String prompt) {

        FirewallResult firewall =
                firewallService.inspect(prompt);

        if (!firewall.isAllowed()) {

            metricsService.increment(
                    MetricsConstants.FIREWALL_BLOCKED);

            throw new BusinessException(
                    firewall.getReason());

        }

        PolicyResult policy =
                policyEngineService.evaluate(prompt);

        if (!policy.isAllowed()) {

            metricsService.increment(
                    MetricsConstants.POLICY_BLOCKED);

            throw new BusinessException(
                    policy.getReason());

        }

    }

    private MaskingResult maskPrompt(
            UUID requestId,
            String prompt) {

        MaskingResult result =
                piiDetectionService.mask(prompt);

        tokenVaultService.save(
                requestId,
                result.getDetectedValues());

        return result;

    }

    private AIRequest buildAIRequest(
            UUID requestId,
            ChatRequest request,
            AuthenticationContext auth,
            String prompt) {

        try {
            RoutingDecision routingDecision =
                    routingService.route(
                            new RoutingContext(
                                    request,
                                    auth));

            routingAnalyticsService.recordDecision(
                    routingDecision);

            metricsService.increment(
                    MetricsConstants.ROUTING_DECISIONS);

            switch (routingDecision.strategy()) {

                case EXPLICIT_PROVIDER ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_EXPLICIT_PROVIDER);

                case EXPLICIT_MODEL ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_EXPLICIT_MODEL);

                case POLICY_BASED ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_POLICY_BASED);

                case TENANT_DEFAULT ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_TENANT_DEFAULT);
            }

            Provider selectedProvider =
                    routingDecision.provider();

            String selectedModel =
                    routingDecision.model();

            log.info(
                    "Routing decision: requestId={} tenant={} strategy={} provider={} model={}",
                    requestId,
                    auth.getTenantCode(),
                    routingDecision.strategy(),
                    routingDecision.provider(),
                    routingDecision.model()
            );

            var metadata = routingDecision.metadata();
            performanceLogger.routingDecision(
                    requestId,
                    routingDecision.strategy() == null ? null : routingDecision.strategy().name(),
                    routingDecision.provider() == null ? null : routingDecision.provider().name(),
                    routingDecision.model(),
                    metadata == null ? null : metadata.selectedScore(),
                    metadata == null ? null : metadata.selectedRank(),
                    metadata == null ? null : metadata.candidateCount(),
                    metadata == null ? null : metadata.selectionReason(),
                    metadata == null || metadata.explanation() == null
                            ? null
                            : String.join(",", metadata.explanation().appliedSignals()));

            metricsService.incrementProviderRequest(
                    selectedProvider);

            return AIRequest.builder()
                    .provider(selectedProvider)
                    .model(selectedModel)
                    .prompt(prompt)
                    .routingDecisionMetadata(routingDecision.metadata())
                    .routingStrategy(routingDecision.strategy())
                    .build();
        } catch (Exception ex) {

            log.warn(
                    "Routing failed: requestId={} tenant={} provider={} model={} error={}",
                    requestId,
                    auth != null
                            ? auth.getTenantCode()
                            : null,
                    request.getProvider(),
                    request.getModel(),
                    ex.getMessage()
            );

            throw ex;
        }
    }
    private AIResponse invokeProvider(
            AIRequest request) {

        return providerFailoverService.execute(request);
    }

    private String restoreResponse(
            UUID requestId,
            AIResponse response) {

        return restoreService.restore(
                response.getResponse(),
                requestId);

    }

    private RagMetadata buildRagMetadata(
            com.ai.gateway.rag.api.RagRequest request,
            RagAugmentationResult result) {

        if (request == null || !request.isEnabled()) {
            return null;
        }

        java.util.List<RagSourceMetadata> sources = result.getChunks().stream()
                .map(chunk -> RagSourceMetadata.builder()
                        .knowledgeBaseId(chunk.getKnowledgeBaseId())
                        .documentId(chunk.getDocumentId())
                        .fileName(chunk.getFileName())
                        .chunkIndex(chunk.getChunkIndex())
                        .similarity(chunk.getSimilarity())
                        .build())
                .toList();

        return RagMetadata.builder()
                .enabled(true)
                .retrievalStrategy(request.getRetrievalStrategy())
                .knowledgeBaseCount(result.getKnowledgeBaseCount())
                .retrievedCount(result.getRetrievedCount())
                .selectedCount(result.getSelectedCount())
                .deduplicatedCount(result.getDeduplicatedCount())
                .droppedCount(result.getDroppedCount())
                .truncatedCount(result.getTruncatedCount())
                .estimatedContextTokens(result.getEstimatedContextTokens())
                .contextTokenBudget(result.getContextTokenBudget())
                .sources(sources)
                .build();
    }

    private void validateFeature(
            AuthenticationContext auth,
            Feature feature) {

        entitlementService.validateFeature(
                auth.getTenantId(),
                feature);
    }




}


