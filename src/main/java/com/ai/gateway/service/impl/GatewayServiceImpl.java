package com.ai.gateway.service.impl;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.*;
import com.ai.gateway.core.contract.*;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.mapper.ProviderFeatureMapper;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.core.failover.ProviderFailoverService;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.core.provider.StreamingAIProvider;
import com.ai.gateway.core.provider.AIProviderFactory;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.governance.service.GovernanceGuardrailService;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.core.metrics.GatewayMetricsService;
import com.ai.gateway.core.metrics.MetricsConstants;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.observability.RequestCorrelationFilter;
import org.slf4j.MDC;
import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.rag.augmentation.RagAugmentationResult;
import com.ai.gateway.rag.augmentation.RagAugmentationService;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.cache.CachedInferenceResponse;
import com.ai.gateway.core.cache.InferenceCacheService;
import com.ai.gateway.core.multimodal.MultimodalRequestValidator;
import com.ai.gateway.core.context.ContextOptimizationResult;
import com.ai.gateway.core.context.ContextOptimizationService;
import com.ai.gateway.core.routing.registry.ModelCapabilities;
import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.RoutingService;
import com.ai.gateway.core.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.core.routing.registry.ProviderModelRegistryService;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.personal.billing.PersonalBillingModeResolver;
import com.ai.gateway.personal.PersonalFeatureEntitlementService;
import com.ai.gateway.personal.billing.PersonalCreditExecutionService;
import com.ai.gateway.personal.intelligence.PersonalSecurityIntelligenceService;
import com.ai.gateway.personal.intelligence.PersonalSecurityRisk;
import com.ai.gateway.personal.security.PersonalChatSecurityProperties;
import com.ai.gateway.personal.quota.service.PersonalQuotaService;
import com.ai.gateway.personal.usage.service.PersonalRequestHistoryService;
import com.ai.gateway.personal.inference.PersonalInferencePersistenceService;
import com.ai.gateway.personal.inference.PersonalTokenVaultService;
import com.ai.gateway.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {

    private static final int DEFAULT_RESERVED_OUTPUT_TOKENS = 1024;

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

    private final MultimodalRequestValidator multimodalRequestValidator;

    private final ContextOptimizationService contextOptimizationService;

    private final InferenceCacheService inferenceCacheService;

    private final AIProviderFactory providerFactory;

    private final PersonalBillingModeResolver personalBillingModeResolver;

    private final PersonalCreditExecutionService personalCreditExecutionService;

    private final PersonalSecurityIntelligenceService personalSecurityIntelligenceService;

    private final PersonalChatSecurityProperties personalChatSecurityProperties;

    private final PersonalFeatureEntitlementService personalFeatureEntitlementService;

    private final PersonalQuotaService personalQuotaService;

    private final PersonalRequestHistoryService personalRequestHistoryService;

    private final PersonalInferencePersistenceService personalInferencePersistenceService;
    private final PersonalTokenVaultService personalTokenVaultService;

    private final ModelRegistry modelRegistry;

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

        UUID inferenceId = personalInferencePersistenceService == null ? null
                : personalInferencePersistenceService.start(
                        auth, requestId, "/api/chat", "CHAT", null);

        AIRequest aiRequest = null;
        boolean providerInvocationStarted = false;
        boolean providerInvocationSucceeded = false;
        PersonalCreditExecutionService.ReservationContext creditReservation = null;
        long providerInvocationStart = 0L;
        Integer estimatedInputTokens = null;
        Integer estimatedOptimizedTokens = null;
        Integer estimatedTokensSaved = null;
        Integer contextWindowTokens = null;
        boolean quotaAcquired = false;

        try {

            multimodalRequestValidator.validate(request);
            addMultimodalCapabilities(request);

            stageStart = System.nanoTime();
            if (request.isExtensiveResearch()) {
                validateFeature(
                        auth,
                        Feature.EXTENSIVE_RESEARCH);
            }
            performanceLogger.stage("ENTITLEMENT", requestId, elapsedMs(stageStart), "SUCCESS");

            stageStart = System.nanoTime();
            // -------------------------------
            // Prompt Firewall
            // Policy Engine
            // -------------------------------
            validateRequest(auth, originalPrompt);
            enforcePersonalSecurity(inferenceId, requestId, auth, originalPrompt);
            performanceLogger.stage("FIREWALL_POLICY", requestId, elapsedMs(stageStart), "SUCCESS");

            // Personal quota enforcement is account-scoped and independent of
            // Business tenant quotas. The estimate is deliberately conservative
            // and provider-neutral; actual provider usage is checked after inference.
            if (auth.isPersonalPrincipal()) {
                long estimatedRequestTokens = Math.max(1L, (originalPrompt == null ? 0 : originalPrompt.length() + 3) / 4);
                personalQuotaService.beforeRequest(auth, estimatedRequestTokens);
                quotaAcquired = true;
            }

            stageStart = System.nanoTime();
            maskedPrompt = maskContextMessages(requestId, inferenceId, request, auth);
            performanceLogger.stage("PII_MASKING_AND_TOKEN_VAULT", requestId, elapsedMs(stageStart), "SUCCESS");
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.addUserPrompt(inferenceId, maskedPrompt);
                personalInferencePersistenceService.event(
                        inferenceId, "PII_MASKING_COMPLETED", "SECURITY",
                        java.util.Map.of("completed", true));
            }

            stageStart = System.nanoTime();
            aiRequest =
                    buildAIRequest(
                            requestId,
                            request,
                            auth,
                            maskedPrompt);
            performanceLogger.stage("ROUTING", requestId, elapsedMs(stageStart), "SUCCESS");
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.updateTarget(inferenceId, aiRequest);
                personalInferencePersistenceService.event(
                        inferenceId, "MODEL_SELECTED", "ROUTING",
                        java.util.Map.of(
                                "provider", aiRequest.getProvider() == null ? "" : aiRequest.getProvider().name(),
                                "model", aiRequest.getModel() == null ? "" : aiRequest.getModel()));
            }

            stageStart = System.nanoTime();
            ContextOptimizationResult contextOptimization =
                    optimizeContext(request, maskedPrompt, aiRequest);
            if (contextOptimization == null) {
                contextOptimization = noOpContextOptimization(maskedPrompt);
            }
            estimatedInputTokens = contextOptimization.getOriginalTokens();
            estimatedOptimizedTokens = contextOptimization.getOptimizedTokens();
            estimatedTokensSaved = contextOptimization.getTokensSaved();
            contextWindowTokens = resolveContextWindow(aiRequest);
            String providerPrompt = contextOptimization.getOptimizedContext();
            performanceLogger.stage(
                    "CONTEXT_OPTIMIZATION",
                    requestId,
                    elapsedMs(stageStart),
                    "strategy=" + contextOptimization.getStrategy()
                            + " provider=" + aiRequest.getProvider()
                            + " model=" + aiRequest.getModel()
                            + " contextWindowTokens=" + resolveContextWindow(aiRequest)
                            + " reservedOutputTokens=" + DEFAULT_RESERVED_OUTPUT_TOKENS
                            + " inputBudgetTokens=" + resolveContextInputBudget(aiRequest)
                            + " estimatedOriginalTokens=" + contextOptimization.getOriginalTokens()
                            + " estimatedOptimizedTokens=" + contextOptimization.getOptimizedTokens()
                            + " estimatedTokensSaved=" + contextOptimization.getTokensSaved());
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.addContext(
                        inferenceId, providerPrompt, estimatedOptimizedTokens);
                personalInferencePersistenceService.event(
                        inferenceId, "CONTEXT_OPTIMIZATION", "CONTEXT",
                        java.util.Map.of(
                                "strategy", contextOptimization.getStrategy(),
                                "originalTokens", contextOptimization.getOriginalTokens(),
                                "optimizedTokens", contextOptimization.getOptimizedTokens(),
                                "tokensSaved", contextOptimization.getTokensSaved()));
            }

            stageStart = System.nanoTime();
            // RAG retrieval must use the current masked user query, not the
            // fully assembled multi-turn provider context. Historical turns can
            // be unrelated to the document the user is asking about.
            RagAugmentationResult ragResult =
                    ragAugmentationService.augment(
                            auth,
                            maskedPrompt,
                            request.getRag());
            providerPrompt = ragResult.getAugmentedPrompt();
            aiRequest.setPrompt(providerPrompt);
            performanceLogger.stage(
                    "RAG_AUGMENTATION",
                    requestId,
                    elapsedMs(stageStart),
                    request.getRag() != null && request.getRag().isEnabled()
                            ? "ENABLED"
                            : "DISABLED");
            if (personalInferencePersistenceService != null && request.getRag() != null && request.getRag().isEnabled()) {
                personalInferencePersistenceService.retrievals(inferenceId, ragResult);
                personalInferencePersistenceService.event(
                        inferenceId, "RAG_RETRIEVAL", "RAG",
                        java.util.Map.of(
                                "retrievedCount", ragResult.getRetrievedCount(),
                                "selectedCount", ragResult.getSelectedCount(),
                                "estimatedContextTokens", ragResult.getEstimatedContextTokens()));
            }

            if (auth.isPersonalPrincipal()) {
                aiRequest.setBillingMode(personalBillingModeResolver
                        .resolve(auth, aiRequest.getProvider(), aiRequest.getModel(), request.getBillingMode())
                        .name());
            }

            stageStart = System.nanoTime();
            Feature feature =
                    ProviderFeatureMapper.toFeature(
                            aiRequest.getProvider());

            validateFeature(
                    auth,
                    feature);
            performanceLogger.stage("PROVIDER_ENTITLEMENT", requestId, elapsedMs(stageStart), "SUCCESS");

            // Phase 10 exact-response cache. Security, firewall/policy, PII
            // masking, RAG augmentation, routing and entitlement checks have
            // already completed, so a cache hit cannot bypass those controls.
            // Extensive research is deliberately excluded from exact caching.
            if (!request.isExtensiveResearch()) {
                stageStart = System.nanoTime();
                CachedInferenceResponse cached =
                        inferenceCacheService.get(auth, aiRequest);
                long cacheLookupLatency = elapsedMs(stageStart);

                if (cached != null) {
                    metricsService.increment(MetricsConstants.INFERENCE_CACHE_HITS);
                    performanceLogger.stage(
                            "INFERENCE_CACHE",
                            requestId,
                            cacheLookupLatency,
                            "HIT");
                    if (personalInferencePersistenceService != null) {
                        personalInferencePersistenceService.event(
                                inferenceId, "CACHE_HIT", "CACHE",
                                java.util.Map.of("lookupLatencyMs", cacheLookupLatency));
                    }

                    AIResponse cachedResponse = AIResponse.builder()
                            .response(cached.response())
                            .provider(cached.provider())
                            .model(cached.model())
                            .build();

                    String restored = restoreResponse(requestId, cachedResponse);
                    performanceLogger.stage(
                            "RESPONSE_RESTORE",
                            requestId,
                            0L,
                            "SUCCESS");

                    long latency = elapsedMs(start);
                    long gatewayOverhead = latency;
                    if (auth.isPersonalPrincipal()) {
                        personalRequestHistoryService.recordSuccess(
                                requestId, auth, aiRequest, cachedResponse, maskedPrompt,
                                latency, 0L, estimatedInputTokens, estimatedOptimizedTokens,
                                estimatedTokensSaved, contextWindowTokens, true,
                                request.getRag() != null && request.getRag().isEnabled());
                    }
                    performanceLogger.requestCompleted(
                            requestId,
                            latency,
                            cached.provider() == null ? null : cached.provider().name(),
                            cached.model(),
                            "CACHE_HIT",
                            0L,
                            gatewayOverhead);

                    return ChatResponse.builder()
                            .requestId(requestId)
                            .response(restored)
                            .rag(buildRagMetadata(request.getRag(), ragResult))
                            .build();
                }

                metricsService.increment(MetricsConstants.INFERENCE_CACHE_MISSES);
                performanceLogger.stage(
                        "INFERENCE_CACHE",
                        requestId,
                        cacheLookupLatency,
                        "MISS");
                if (personalInferencePersistenceService != null) {
                    personalInferencePersistenceService.event(
                            inferenceId, "CACHE_MISS", "CACHE",
                            java.util.Map.of("lookupLatencyMs", cacheLookupLatency));
                }
            }

            // -------------------------------
            // Provider Invocation
            // -------------------------------

            if (auth.isPersonalPrincipal()
                    && "CREDIT".equalsIgnoreCase(aiRequest.getBillingMode())) {
                creditReservation = personalCreditExecutionService.reserve(
                        auth, aiRequest, "inference:" + requestId);
            }

            providerInvocationStarted = true;
            providerInvocationStart = System.nanoTime();
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.event(
                        inferenceId, "PROVIDER_REQUEST_STARTED", "PROVIDER",
                        java.util.Map.of(
                                "provider", aiRequest.getProvider() == null ? "" : aiRequest.getProvider().name(),
                                "model", aiRequest.getModel() == null ? "" : aiRequest.getModel()));
            }

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
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.providerAttempt(
                        inferenceId, 1,
                        aiRequest.getProvider() == null ? null : aiRequest.getProvider().name(),
                        aiRequest.getModel(), "SUCCESS",
                        providerInvocationStart, System.nanoTime(), aiResponse, null);
                personalInferencePersistenceService.event(
                        inferenceId, "PROVIDER_RESPONSE_RECEIVED", "PROVIDER",
                        java.util.Map.of(
                                "provider", aiRequest.getProvider() == null ? "" : aiRequest.getProvider().name(),
                                "model", aiRequest.getModel() == null ? "" : aiRequest.getModel()));
            }

            if (creditReservation != null) {
                personalCreditExecutionService.reconcile(
                        creditReservation,
                        aiRequest,
                        aiResponse);
                creditReservation = null;
            }

            if (auth.isPersonalPrincipal()) {
                personalQuotaService.afterSuccess(auth, aiResponse);
            }

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

            if (!request.isExtensiveResearch()) {
                inferenceCacheService.put(
                        auth,
                        aiRequest,
                        new CachedInferenceResponse(
                                aiResponse.getResponse(),
                                aiResponse.getProvider(),
                                aiResponse.getModel()));
            }

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

            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.completeSuccess(
                        inferenceId, aiRequest, aiResponse, latency,
                        estimatedInputTokens, estimatedOptimizedTokens,
                        estimatedTokensSaved, contextWindowTokens, false,
                        request.getRag() != null && request.getRag().isEnabled());
            }

            if (auth.isPersonalPrincipal()) {
                personalRequestHistoryService.recordSuccess(
                        requestId, auth, aiRequest, aiResponse, maskedPrompt,
                        latency, providerLatency, estimatedInputTokens,
                        estimatedOptimizedTokens, estimatedTokensSaved,
                        contextWindowTokens, false,
                        request.getRag() != null && request.getRag().isEnabled());
            }

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

            if (creditReservation != null) {
                try {
                    personalCreditExecutionService.releaseOnFailure(creditReservation);
                } catch (RuntimeException releaseEx) {
                    log.error("Failed to release Personal credit reservation requestId={}",
                            requestId, releaseEx);
                }
            }

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

            if (personalInferencePersistenceService != null) {
                if (providerInvocationStarted) {
                    personalInferencePersistenceService.providerAttempt(
                            inferenceId, 1,
                            aiRequest == null || aiRequest.getProvider() == null ? null : aiRequest.getProvider().name(),
                            aiRequest == null ? null : aiRequest.getModel(),
                            "FAILED", providerInvocationStart, System.nanoTime(), null, ex);
                }
                if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                    personalInferencePersistenceService.completeBlocked(
                            inferenceId, aiRequest, latency,
                            "PERSONAL_SECURITY_BLOCKED", ex.getMessage());
                } else {
                    personalInferencePersistenceService.completeFailure(
                            inferenceId, aiRequest, latency,
                            ex.getClass().getSimpleName(), ex.getMessage());
                }
            }

            if (auth.isPersonalPrincipal()) {
                if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                    personalRequestHistoryService.recordBlocked(
                            requestId, auth, aiRequest, maskedPrompt,
                            latency, providerLatency, "PERSONAL_SECURITY_BLOCKED");
                } else {
                    personalRequestHistoryService.recordFailure(
                            requestId, auth, aiRequest, maskedPrompt,
                            latency, providerLatency, ex.getClass().getSimpleName());
                }
            }

            if (!(ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException)) {
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
            }
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
        } finally {
            if (quotaAcquired) {
                personalQuotaService.release(auth);
            }
        }

    }


    @Override
    @RequiresFeature(Feature.CHAT)
    public StreamAdmission preflightStream(ChatRequest request) {
        return preflightStream(request, "/api/chat/stream", "STREAM");
    }

    @Override
    @RequiresFeature(Feature.CHAT)
    public StreamAdmission preflightStream(ChatRequest request, String endpoint, String operation) {
        metricsService.increment(MetricsConstants.TOTAL_REQUESTS);

        UUID requestId = resolveRequestId();
        long start = System.nanoTime();
        performanceLogger.requestStart(requestId, endpoint);

        AuthenticationContext auth = getAuthenticationContext();
        validateFeature(auth, Feature.STREAMING);

        UUID inferenceId = personalInferencePersistenceService == null ? null
                : personalInferencePersistenceService.start(
                        auth, requestId, endpoint, operation, null);

        try {
            multimodalRequestValidator.validate(request);
            addMultimodalCapabilities(request);

            long stageStart = System.nanoTime();
            if (request.isExtensiveResearch()) {
                validateFeature(auth, Feature.EXTENSIVE_RESEARCH);
            }
            performanceLogger.stage(
                    "ENTITLEMENT", requestId, elapsedMs(stageStart), "SUCCESS");

            stageStart = System.nanoTime();
            validateRequest(auth, request.getPrompt());
            enforcePersonalSecurity(inferenceId, requestId, auth, request.getPrompt());
            performanceLogger.stage(
                    "FIREWALL_POLICY", requestId, elapsedMs(stageStart), "SUCCESS");

            return new StreamAdmission(requestId, inferenceId, auth, start);
        } catch (Exception ex) {
            long latency = elapsedMs(start);
            if (personalInferencePersistenceService != null && inferenceId != null) {
                if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                    personalInferencePersistenceService.completeBlocked(
                            inferenceId, null, latency,
                            "PERSONAL_SECURITY_BLOCKED", ex.getMessage());
                } else {
                    personalInferencePersistenceService.completeFailure(
                            inferenceId, null, latency,
                            ex.getClass().getSimpleName(), ex.getMessage());
                }
            }
            if (auth.isPersonalPrincipal()) {
                if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                    personalRequestHistoryService.recordBlocked(
                            requestId, auth, null, request.getPrompt(),
                            latency, 0L, "PERSONAL_SECURITY_BLOCKED");
                } else {
                    personalRequestHistoryService.recordFailure(
                            requestId, auth, null, request.getPrompt(),
                            latency, 0L, ex.getClass().getSimpleName());
                }
            }
            performanceLogger.requestCompleted(
                    requestId, latency, null, null, "STREAM_ADMISSION_FAILED", 0L, latency);
            throw ex;
        }
    }

    @Override
    @RequiresFeature(Feature.CHAT)
    public void stream(
            ChatRequest request,
            Consumer<GatewayStreamEvent> eventConsumer) {
        StreamAdmission admission = preflightStream(request);
        stream(request, admission, eventConsumer);
    }

    @Override
    @RequiresFeature(Feature.CHAT)
    public void stream(
            ChatRequest request,
            StreamAdmission admission,
            Consumer<GatewayStreamEvent> eventConsumer) {

        UUID requestId = admission.requestId();
        long start = admission.startedAtNanos();

        AuthenticationContext auth = admission.authenticationContext();
        UUID inferenceId = admission.inferenceId();

        AIRequest aiRequest = null;
        String maskedPrompt = request.getPrompt();
        long providerStart = 0L;
        boolean providerInvocationStarted = false;
        boolean providerInvocationSucceeded = false;
        boolean successPersisted = false;
        PersonalCreditExecutionService.ReservationContext creditReservation = null;
        boolean quotaAcquired = false;
        Integer estimatedInputTokens = null;
        Integer estimatedOptimizedTokens = null;
        Integer estimatedTokensSaved = null;
        Integer contextWindowTokens = null;
        long stageStart;

        try {
            // Request admission, including the Personal Firewall, is performed
            // before StreamingResponseBody is returned by the controller. Do not
            // repeat it here: once SSE starts, an exception can no longer change
            // the HTTP status to 403/503.
            if (auth.isPersonalPrincipal()) {
                long estimatedRequestTokens = Math.max(1L, (request.getPrompt() == null ? 0 : request.getPrompt().length() + 3) / 4);
                personalQuotaService.beforeRequest(auth, estimatedRequestTokens);
                quotaAcquired = true;
            }

            stageStart = System.nanoTime();
            maskedPrompt = maskContextMessages(requestId, inferenceId, request, auth);
            performanceLogger.stage(
                    "PII_MASKING_AND_TOKEN_VAULT",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.addUserPrompt(inferenceId, maskedPrompt);
                personalInferencePersistenceService.event(
                        inferenceId, "PII_MASKING_COMPLETED", "SECURITY",
                        java.util.Map.of("completed", true));
            }

            stageStart = System.nanoTime();
            aiRequest = buildAIRequest(
                    requestId,
                    request,
                    auth,
                    maskedPrompt);
            performanceLogger.stage(
                    "ROUTING", requestId, elapsedMs(stageStart), "SUCCESS");
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.updateTarget(inferenceId, aiRequest);
                personalInferencePersistenceService.event(
                        inferenceId, "MODEL_SELECTED", "ROUTING",
                        java.util.Map.of(
                                "provider", aiRequest.getProvider() == null ? "" : aiRequest.getProvider().name(),
                                "model", aiRequest.getModel() == null ? "" : aiRequest.getModel()));
            }

            stageStart = System.nanoTime();
            ContextOptimizationResult contextOptimization =
                    optimizeContext(request, maskedPrompt, aiRequest);
            if (contextOptimization == null) {
                contextOptimization = noOpContextOptimization(maskedPrompt);
            }
            estimatedInputTokens = contextOptimization.getOriginalTokens();
            estimatedOptimizedTokens = contextOptimization.getOptimizedTokens();
            estimatedTokensSaved = contextOptimization.getTokensSaved();
            contextWindowTokens = resolveContextWindow(aiRequest);
            String providerPrompt = contextOptimization.getOptimizedContext();
            performanceLogger.stage(
                    "CONTEXT_OPTIMIZATION",
                    requestId,
                    elapsedMs(stageStart),
                    "strategy=" + contextOptimization.getStrategy()
                            + " provider=" + aiRequest.getProvider()
                            + " model=" + aiRequest.getModel()
                            + " contextWindowTokens=" + resolveContextWindow(aiRequest)
                            + " reservedOutputTokens=" + DEFAULT_RESERVED_OUTPUT_TOKENS
                            + " inputBudgetTokens=" + resolveContextInputBudget(aiRequest)
                            + " estimatedOriginalTokens=" + contextOptimization.getOriginalTokens()
                            + " estimatedOptimizedTokens=" + contextOptimization.getOptimizedTokens()
                            + " estimatedTokensSaved=" + contextOptimization.getTokensSaved());

            stageStart = System.nanoTime();
            // RAG retrieval must use the current masked user query, not the
            // fully assembled multi-turn provider context. Historical turns can
            // be unrelated to the document the user is asking about.
            RagAugmentationResult ragResult =
                    ragAugmentationService.augment(
                            auth,
                            maskedPrompt,
                            request.getRag());
            providerPrompt = ragResult.getAugmentedPrompt();
            aiRequest.setPrompt(providerPrompt);
            performanceLogger.stage(
                    "RAG_AUGMENTATION",
                    requestId,
                    elapsedMs(stageStart),
                    request.getRag() != null && request.getRag().isEnabled()
                            ? "ENABLED" : "DISABLED");

            if (auth.isPersonalPrincipal()) {
                aiRequest.setBillingMode(personalBillingModeResolver
                        .resolve(auth, aiRequest.getProvider(), aiRequest.getModel(), request.getBillingMode())
                        .name());
            }

            stageStart = System.nanoTime();
            Feature feature =
                    ProviderFeatureMapper.toFeature(aiRequest.getProvider());
            validateFeature(auth, feature);
            performanceLogger.stage(
                    "PROVIDER_ENTITLEMENT",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");

            eventConsumer.accept(GatewayStreamEvent.builder()
                    .requestId(requestId)
                    .type("start")
                    .provider(aiRequest.getProvider().name())
                    .model(aiRequest.getModel())
                    .build());

            // Phase 10 exact-response cache for streaming requests. Security,
            // policy, PII, RAG, routing and entitlement checks have already run.
            if (!request.isExtensiveResearch()) {
                stageStart = System.nanoTime();
                CachedInferenceResponse cached = inferenceCacheService.get(auth, aiRequest);
                long cacheLookupLatency = elapsedMs(stageStart);
                if (cached != null) {
                    metricsService.increment(MetricsConstants.INFERENCE_CACHE_HITS);
                    performanceLogger.stage("INFERENCE_CACHE", requestId, cacheLookupLatency, "HIT");
                    if (personalInferencePersistenceService != null) {
                        personalInferencePersistenceService.event(
                                inferenceId, "CACHE_HIT", "CACHE",
                                java.util.Map.of("lookupLatencyMs", cacheLookupLatency));
                    }
                    AIResponse cachedResponse = AIResponse.builder()
                            .response(cached.response())
                            .provider(cached.provider())
                            .model(cached.model())
                            .usage(Usage.builder().inputTokens(0).outputTokens(0).totalTokens(0)
                                    .latencyMs(cacheLookupLatency).build())
                            .build();
                    String restored = restoreResponse(requestId, cachedResponse);
                    eventConsumer.accept(GatewayStreamEvent.builder()
                            .requestId(requestId).type("delta").content(restored).build());
                    long latency = elapsedMs(start);
                    if (personalInferencePersistenceService != null) {
                        personalInferencePersistenceService.completeSuccess(
                                inferenceId, aiRequest, cachedResponse, latency,
                                estimatedInputTokens, estimatedOptimizedTokens,
                                estimatedTokensSaved, contextWindowTokens, true,
                                request.getRag() != null && request.getRag().isEnabled());
                    }
                    if (auth.isPersonalPrincipal()) {
                        personalRequestHistoryService.recordSuccess(
                                requestId, auth, aiRequest, cachedResponse, maskedPrompt,
                                latency, 0L, estimatedInputTokens, estimatedOptimizedTokens,
                                estimatedTokensSaved, contextWindowTokens, true,
                                request.getRag() != null && request.getRag().isEnabled());
                    }
                    performanceLogger.requestCompleted(
                            requestId, latency,
                            cached.provider() == null ? null : cached.provider().name(),
                            cached.model(), "CACHE_HIT", 0L, latency);
                    eventConsumer.accept(GatewayStreamEvent.builder()
                            .requestId(requestId).type("done")
                            .provider(cached.provider() == null ? null : cached.provider().name())
                            .model(cached.model()).inputTokens(0).outputTokens(0).totalTokens(0)
                            .latencyMs(latency).build());
                    successPersisted = true;
                    return;
                }
                metricsService.increment(MetricsConstants.INFERENCE_CACHE_MISSES);
                performanceLogger.stage("INFERENCE_CACHE", requestId, cacheLookupLatency, "MISS");
                if (personalInferencePersistenceService != null) {
                    personalInferencePersistenceService.event(
                            inferenceId, "CACHE_MISS", "CACHE",
                            java.util.Map.of("lookupLatencyMs", cacheLookupLatency));
                }
            }

            if (auth.isPersonalPrincipal()
                    && "CREDIT".equalsIgnoreCase(aiRequest.getBillingMode())) {
                creditReservation = personalCreditExecutionService.reserve(
                        auth, aiRequest, "inference:" + requestId);
            }

            var provider = providerFactory.getProvider(aiRequest.getProvider());
            if (!(provider instanceof StreamingAIProvider streamingProvider)) {
                throw new UnsupportedOperationException(
                        "Streaming is not supported by provider "
                                + aiRequest.getProvider()
                                + " / "
                                + aiRequest.getModel());
            }

            providerStart = System.nanoTime();
            providerInvocationStarted = true;
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.event(
                        inferenceId, "PROVIDER_REQUEST_STARTED", "PROVIDER",
                        java.util.Map.of(
                                "provider", aiRequest.getProvider() == null ? "" : aiRequest.getProvider().name(),
                                "model", aiRequest.getModel() == null ? "" : aiRequest.getModel()));
            }
            StringBuilder restoredSoFar = new StringBuilder();
            String[] lastEmitted = {""};

            AIStreamResult result = streamingProvider.stream(
                    aiRequest,
                    delta -> {
                        if (delta == null || delta.isEmpty()) {
                            return;
                        }

                        restoredSoFar.append(delta);
                        String restored = restoreService.restore(
                                restoredSoFar.toString(),
                                requestId);
                        String emitted = lastEmitted[0];

                        if (restored.startsWith(emitted)
                                && restored.length() > emitted.length()) {
                            String suffix =
                                    restored.substring(emitted.length());
                            lastEmitted[0] = restored;
                            eventConsumer.accept(
                                    GatewayStreamEvent.builder()
                                            .requestId(requestId)
                                            .type("delta")
                                            .content(suffix)
                                            .build());
                        } else if (!restored.equals(emitted)) {
                            lastEmitted[0] = restored;
                            eventConsumer.accept(
                                    GatewayStreamEvent.builder()
                                            .requestId(requestId)
                                            .type("replace")
                                            .content(restored)
                                            .build());
                        }
                    });
            providerInvocationSucceeded = true;

            long providerLatency = elapsedMs(providerStart);
            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.providerAttempt(
                        inferenceId, 1,
                        result.getProvider() == null ? null : result.getProvider().name(),
                        result.getModel(), "SUCCESS",
                        providerStart, System.nanoTime(), AIResponse.builder()
                                .response(result.getResponse())
                                .provider(result.getProvider())
                                .model(result.getModel())
                                .usage(Usage.builder()
                                        .inputTokens(result.getInputTokens())
                                        .outputTokens(result.getOutputTokens())
                                        .totalTokens(result.getTotalTokens())
                                        .build())
                                .build(), null);
                personalInferencePersistenceService.event(
                        inferenceId, "PROVIDER_RESPONSE_RECEIVED", "PROVIDER",
                        java.util.Map.of("provider", result.getProvider() == null ? "" : result.getProvider().name(),
                                "model", result.getModel() == null ? "" : result.getModel()));
            }

            AIResponse finalResponse = AIResponse.builder()
                    .response(result.getResponse())
                    .provider(result.getProvider())
                    .model(result.getModel())
                    .usage(Usage.builder()
                            .inputTokens(result.getInputTokens() == null ? 0 : result.getInputTokens())
                            .outputTokens(result.getOutputTokens() == null ? 0 : result.getOutputTokens())
                            .totalTokens(result.getTotalTokens() == null ? 0 : result.getTotalTokens())
                            .latencyMs(result.getLatencyMs() == null ? providerLatency : result.getLatencyMs())
                            .build())
                    .build();

            aiRequest.setProvider(result.getProvider());
            aiRequest.setModel(result.getModel());

            if (creditReservation != null) {
                personalCreditExecutionService.reconcile(
                        creditReservation,
                        aiRequest,
                        finalResponse);
                creditReservation = null;
            }

            if (auth.isPersonalPrincipal()) {
                personalQuotaService.afterSuccess(auth, finalResponse);
            }

            stageStart = System.nanoTime();
            enforcePostProviderGuardrails(
                    requestId,
                    auth,
                    aiRequest,
                    finalResponse);
            performanceLogger.stage(
                    "GOVERNANCE_GUARDRAILS",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");

            stageStart = System.nanoTime();
            restoreService.restore(result.getResponse(), requestId);
            performanceLogger.stage(
                    "RESPONSE_RESTORE",
                    requestId,
                    elapsedMs(stageStart),
                    "SUCCESS");

            long latency = elapsedMs(start);
            if (!request.isExtensiveResearch()) {
                inferenceCacheService.put(
                        auth,
                        aiRequest,
                        new CachedInferenceResponse(
                                finalResponse.getResponse(),
                                finalResponse.getProvider(),
                                finalResponse.getModel()));
            }

            if (personalInferencePersistenceService != null) {
                personalInferencePersistenceService.completeSuccess(
                        inferenceId, aiRequest, finalResponse, latency,
                        estimatedInputTokens, estimatedOptimizedTokens,
                        estimatedTokensSaved, contextWindowTokens, false,
                        request.getRag() != null && request.getRag().isEnabled());
            }

            if (auth.isPersonalPrincipal()) {
                personalRequestHistoryService.recordSuccess(
                        requestId, auth, aiRequest, finalResponse, maskedPrompt,
                        latency, providerLatency, estimatedInputTokens,
                        estimatedOptimizedTokens, estimatedTokensSaved,
                        contextWindowTokens, false,
                        request.getRag() != null && request.getRag().isEnabled());
            }

            postProviderPersistenceService.persistSuccess(
                    requestId,
                    auth,
                    aiRequest,
                    finalResponse,
                    new RoutingDecision(
                            aiRequest.getProvider(),
                            aiRequest.getModel(),
                            aiRequest.getRoutingStrategy(),
                            aiRequest.getRoutingDecisionMetadata()),
                    providerLatency,
                    maskedPrompt,
                    latency);
            successPersisted = true;

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
                    "STREAM_SUCCESS",
                    providerLatency,
                    Math.max(0L, latency - providerLatency));

            eventConsumer.accept(GatewayStreamEvent.builder()
                    .requestId(requestId)
                    .type("done")
                    .provider(aiRequest.getProvider().name())
                    .model(aiRequest.getModel())
                    .inputTokens(result.getInputTokens())
                    .outputTokens(result.getOutputTokens())
                    .totalTokens(result.getTotalTokens())
                    .latencyMs(latency)
                    .build());

        } catch (StreamClientDisconnectedException ex) {
            if (creditReservation != null) {
                personalCreditExecutionService.releaseOnFailure(creditReservation);
                creditReservation = null;
            }
            long latency = elapsedMs(start);
            long providerLatency = providerInvocationStarted
                    ? elapsedMs(providerStart) : 0L;

            if (!successPersisted) {
                if (auth.isPersonalPrincipal()) {
                    personalRequestHistoryService.recordFailure(
                            requestId, auth, aiRequest, maskedPrompt,
                            latency, providerLatency, "CLIENT_DISCONNECT");
                }
                postProviderPersistenceService.persistFailure(
                        requestId,
                        auth,
                        aiRequest,
                        aiRequest == null ? null :
                                new RoutingDecision(
                                        aiRequest.getProvider(),
                                        aiRequest.getModel(),
                                        aiRequest.getRoutingStrategy(),
                                        aiRequest.getRoutingDecisionMetadata()),
                        providerLatency,
                        maskedPrompt,
                        latency,
                        providerInvocationStarted && !providerInvocationSucceeded,
                        "CLIENT_DISCONNECT");
                performanceLogger.stage(
                        "POST_PROVIDER_PERSISTENCE_ASYNC",
                        requestId,
                        0L,
                        "QUEUED");
            }

            performanceLogger.requestCompleted(
                    requestId,
                    latency,
                    aiRequest != null && aiRequest.getProvider() != null
                            ? aiRequest.getProvider().name() : null,
                    aiRequest != null ? aiRequest.getModel() : null,
                    "STREAM_CLIENT_DISCONNECTED",
                    providerLatency,
                    Math.max(0L, latency - providerLatency));

            // The socket is already gone. Never attempt a second SSE write.
            return;

        } catch (Exception ex) {
            if (creditReservation != null) {
                personalCreditExecutionService.releaseOnFailure(creditReservation);
                creditReservation = null;
            }
            long latency = elapsedMs(start);
            long providerLatency = providerInvocationStarted
                    ? elapsedMs(providerStart) : 0L;

            if (!successPersisted) {
                if (personalInferencePersistenceService != null) {
                    if (providerInvocationStarted) {
                        personalInferencePersistenceService.providerAttempt(
                                inferenceId, 1,
                                aiRequest == null || aiRequest.getProvider() == null ? null : aiRequest.getProvider().name(),
                                aiRequest == null ? null : aiRequest.getModel(),
                                "FAILED", providerStart, System.nanoTime(), null, ex);
                    }
                    if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                        personalInferencePersistenceService.completeBlocked(
                                inferenceId, aiRequest, latency,
                                "PERSONAL_SECURITY_BLOCKED", ex.getMessage());
                    } else {
                        personalInferencePersistenceService.completeFailure(
                                inferenceId, aiRequest, latency,
                                ex.getClass().getSimpleName(), ex.getMessage());
                    }
                }
                if (auth.isPersonalPrincipal()) {
                    if (ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException) {
                        personalRequestHistoryService.recordBlocked(
                                requestId, auth, aiRequest, maskedPrompt,
                                latency, providerLatency, "PERSONAL_SECURITY_BLOCKED");
                    } else {
                        personalRequestHistoryService.recordFailure(
                                requestId, auth, aiRequest, maskedPrompt,
                                latency, providerLatency, ex.getClass().getSimpleName());
                    }
                }
                if (!(ex instanceof com.ai.gateway.personal.security.PersonalSecurityBlockedException)) {
                    postProviderPersistenceService.persistFailure(
                            requestId,
                            auth,
                            aiRequest,
                            aiRequest == null ? null :
                                    new RoutingDecision(
                                            aiRequest.getProvider(),
                                            aiRequest.getModel(),
                                            aiRequest.getRoutingStrategy(),
                                            aiRequest.getRoutingDecisionMetadata()),
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
                }
            }

            performanceLogger.requestCompleted(
                    requestId,
                    latency,
                    aiRequest != null && aiRequest.getProvider() != null
                            ? aiRequest.getProvider().name() : null,
                    aiRequest != null ? aiRequest.getModel() : null,
                    "STREAM_FAILED",
                    providerLatency,
                    Math.max(0L, latency - providerLatency));

            /*
             * The HTTP status is normally already committed after the first
             * SSE event. Therefore stream failures are represented as a
             * terminal SSE error event, not as a second HTTP response.
             */
            eventConsumer.accept(GatewayStreamEvent.builder()
                    .requestId(requestId)
                    .type("error")
                    .error(streamErrorMessage(ex))
                    .build());
        } finally {
            if (quotaAcquired) {
                personalQuotaService.release(auth);
            }
        }
    }

    private String streamErrorMessage(Throwable ex) {
        Throwable current = ex;

        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return "Provider request timed out.";
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);

                if (normalized.contains("timed out")
                        || normalized.contains("timeout")
                        || normalized.contains("read timed out")
                        || normalized.contains("connect timed out")) {
                    return "Provider request timed out.";
                }
            }

            current = current.getCause();
        }

        if (ex instanceof UnsupportedOperationException) {
            return ex.getMessage();
        }

        return "Streaming request failed.";
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

    private void enforcePersonalSecurity(
            UUID inferenceId,
            UUID requestId,
            AuthenticationContext auth,
            String prompt) {

        if (auth == null || !auth.isPersonalPrincipal()) {
            return;
        }

        long securityStart = System.nanoTime();
        if (personalInferencePersistenceService != null) {
            personalInferencePersistenceService.event(
                    inferenceId, "SECURITY_CHECK_STARTED", "SECURITY",
                    java.util.Map.of("requestId", requestId.toString()));
        }

        var securityAssessment =
                personalSecurityIntelligenceService.assess(requestId, prompt);

        if (personalInferencePersistenceService != null) {
            personalInferencePersistenceService.security(inferenceId, securityAssessment);
            personalInferencePersistenceService.securityDecision(inferenceId, securityAssessment);
        }

        long securityLatency = elapsedMs(securityStart);
        performanceLogger.stage(
                "PERSONAL_SECURITY_FIREWALL",
                requestId,
                securityLatency,
                securityAssessment.decision() + ":"
                        + securityAssessment.risk().name());

        if (securityAssessment.shouldBlock()) {
            throw new com.ai.gateway.personal.security.PersonalSecurityBlockedException(
                    requestId, securityAssessment);
        }
    }

    private void validateRequest(
            AuthenticationContext auth,
            String prompt) {

        // Personal Chat is intentionally not governed by the legacy
        // Business hard-block firewall/policy rules. Those rules are
        // designed for tenant governance and contain restrictions such as
        // command execution, filesystem access and code-generation blocks
        // that are inappropriate for a personal AI workspace.
        //
        // Personal Chat uses PersonalSecurityIntelligenceService below as
        // its adaptive pre-provider security layer. Hard rules remain
        // available as an explicit opt-in for deployments that require them.
        if (auth != null
                && auth.isPersonalPrincipal()
                && !personalChatSecurityProperties.isHardRulesEnabled()) {
            return;
        }

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
            String prompt,
            AuthenticationContext auth) {

        MaskingResult result =
                piiDetectionService.mask(prompt);

        if (auth != null && auth.isPersonalPrincipal()) {
            personalTokenVaultService.save(auth, requestId, result.getDetectedValues());
        } else {
            tokenVaultService.save(requestId, result.getDetectedValues());
        }

        return result;

    }

    private String maskContextMessages(
            UUID requestId,
            UUID inferenceId,
            ChatRequest request,
            AuthenticationContext auth) {

        /*
         * Context messages are historical turns. The current prompt is a
         * separate request field and must always be masked independently.
         *
         * Previously, when contextMessages was non-empty, this method returned
         * only the masked historical messages and never masked/returned the
         * current prompt. That made the downstream context optimizer select
         * only the previous conversation, effectively making every response
         * one turn behind.
         */
        if (request.getContextMessages() != null) {
            for (ContextMessage message : request.getContextMessages()) {
                if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }

                MaskingResult masked = piiDetectionService.mask(message.getContent());
                if (auth != null && auth.isPersonalPrincipal()) {
                    personalTokenVaultService.save(auth, requestId, masked.getDetectedValues());
                } else {
                    tokenVaultService.save(requestId, masked.getDetectedValues());
                }
                recordPiiTelemetry(inferenceId, masked);
                message.setContent(masked.getMaskedPrompt());
            }
        }

        MaskingResult currentPrompt = maskPrompt(requestId, request.getPrompt(), auth);
        recordPiiTelemetry(inferenceId, currentPrompt);
        return currentPrompt.getMaskedPrompt();
    }

    private void recordPiiTelemetry(UUID inferenceId, MaskingResult result) {
        if (personalInferencePersistenceService == null || inferenceId == null || result == null) {
            return;
        }
        java.util.List<String> categories = result.getDetectedValues() == null
                ? java.util.List.of()
                : result.getDetectedValues().stream()
                    .map(value -> value.getPiiType() == null ? "UNKNOWN" : value.getPiiType().name())
                    .distinct()
                    .toList();
        int count = result.getDetectedValues() == null ? 0 : result.getDetectedValues().size();
        personalInferencePersistenceService.pii(
                inferenceId, categories, count, count > 0);
    }

    private ContextOptimizationResult noOpContextOptimization(String context) {
        String value = context == null ? "" : context;
        int tokens = Math.max(1, (value.length() + 3) / 4);
        return ContextOptimizationResult.builder()
                .originalContext(value)
                .optimizedContext(value)
                .originalTokens(tokens)
                .optimizedTokens(tokens)
                .tokensSaved(0)
                .duplicateSegmentsRemoved(0)
                .compressed(false)
                .strategy("NO_OP_FALLBACK")
                .build();
    }

    private ContextOptimizationResult optimizeContext(
            ChatRequest request,
            String maskedPrompt,
            AIRequest aiRequest) {

        int budget = resolveContextInputBudget(aiRequest);

        java.util.List<ContextMessage> messages = new java.util.ArrayList<>();

        if (request.getContextMessages() != null) {
            messages.addAll(
                    request.getContextMessages().stream()
                            .filter(message -> message != null
                                    && message.getContent() != null
                                    && !message.getContent().isBlank())
                            .toList());
        }

        /*
         * ChatRequest.prompt is the current user turn. It is not part of
         * contextMessages by contract, so append it explicitly as the latest
         * user message before optimization. Without this, any multi-turn
         * conversation sends only the previous turns to the provider.
         */
        if (maskedPrompt != null && !maskedPrompt.isBlank()) {
            messages.add(ContextMessage.builder()
                    .role("user")
                    .content(maskedPrompt)
                    .build());
        }

        if (!messages.isEmpty()) {
            return contextOptimizationService.optimize(messages, budget);
        }

        return contextOptimizationService.optimize(maskedPrompt, budget);
    }

    private int resolveContextWindow(AIRequest request) {
        if (request == null || request.getModel() == null) {
            return 16000;
        }
        return modelRegistry.find(request.getProvider(), request.getModel())
                .map(ModelDefinition::contextWindowTokens)
                .filter(value -> value > 0)
                .orElse(16000);
    }

    private int resolveContextInputBudget(AIRequest request) {
        if (request == null) {
            return 15000;
        }
        return modelRegistry.find(request.getProvider(), request.getModel())
                .map(model -> Math.max(256, model.contextWindowTokens() - DEFAULT_RESERVED_OUTPUT_TOKENS))
                .orElse(Math.max(256, 16000 - DEFAULT_RESERVED_OUTPUT_TOKENS));
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
                    .maximumRequestCost(request.getMaximumRequestCost())
                    .routingDecisionMetadata(routingDecision.metadata())
                    .routingStrategy(routingDecision.strategy())
                    .media(request.getMedia())
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

    private void addMultimodalCapabilities(ChatRequest request) {
        if (request.getMedia() == null || request.getMedia().isEmpty()) {
            return;
        }
        java.util.LinkedHashSet<String> capabilities =
                new java.util.LinkedHashSet<>(request.getRequiredCapabilities() == null
                        ? java.util.Set.of() : request.getRequiredCapabilities());
        for (MediaContent media : request.getMedia()) {
            if (media != null && media.getType() == MediaTypeKind.IMAGE) {
                capabilities.add(ModelCapabilities.VISION);
            } else if (media != null && media.getType() == MediaTypeKind.AUDIO) {
                capabilities.add(ModelCapabilities.AUDIO);
            }
        }
        request.setRequiredCapabilities(java.util.Set.copyOf(capabilities));
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

        if (auth != null && auth.isPersonalPrincipal()) {
            personalFeatureEntitlementService.validate(auth, feature);
        } else {
            entitlementService.validateFeature(
                    auth.getTenantId(),
                    feature);
        }
    }




}


