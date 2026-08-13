package com.ai.gateway.service.impl;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.*;
import com.ai.gateway.entitlement.annotation.RequiresFeature;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.mapper.ProviderFeatureMapper;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.service.PolicyEngineService;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import com.ai.gateway.service.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {

    private final PIIDetectionService piiDetectionService;

    private final TokenVaultService tokenVaultService;

    private final RestoreService restoreService;

    private final AuditService auditService;

    private final AIProviderFactory providerFactory;

    private final PromptFireWallService firewallService;

    private final PolicyEngineService policyEngineService;

    private final GatewayMetricsService metricsService;

    private final TokenUsageService tokenUsageService;

    private final CostService costService;

    private final EntitlementService entitlementService;

    private final QuotaService quotaService;

    private final RoutingService routingService;

  //  private final BudgetService budgetService;

    @Override
    @RequiresFeature(Feature.CHAT)
    public ChatResponse process(ChatRequest request) {

        metricsService.increment(MetricsConstants.TOTAL_REQUESTS);

        UUID requestId = UUID.randomUUID();

        long start = System.currentTimeMillis();

        String originalPrompt = request.getPrompt();

        String maskedPrompt = originalPrompt;



        AuthenticationContext auth = getAuthenticationContext();
        AIRequest aiRequest = null;



        try {


            // -------------------------------
            // Prompt Firewall
            // Policy Engine
            // -------------------------------
            validateRequest(originalPrompt);



            MaskingResult maskingResult =
                    maskPrompt(requestId, originalPrompt);

            maskedPrompt =
                    maskingResult.getMaskedPrompt();


            aiRequest =
                    buildAIRequest(
                            requestId,
                            request,
                            auth,
                            maskedPrompt);

            Feature feature =
                    ProviderFeatureMapper.toFeature(
                            aiRequest.getProvider());

            validateFeature(
                    auth,
                    feature);
            // -------------------------------
            // Provider Invocation
            // -------------------------------

            AIResponse aiResponse =
                    invokeProvider(aiRequest);

            // -------------------------------
            // Token Usage
            // -------------------------------


            persistUsage(
                    requestId,
                    auth,
                    aiRequest,
                    aiResponse);
            // -------------------------------
            // Restore Response
            // -------------------------------

            String restored =
                    restoreResponse(
                            requestId,
                            aiResponse);

            long latency =
                    System.currentTimeMillis() - start;

            auditSuccess(
                    requestId,
                    maskedPrompt,
                    aiRequest,
                    aiResponse,
                    latency);

            return ChatResponse.builder()
                    .requestId(requestId)
                    .response(restored)
                    .build();

        } catch (Exception ex) {

            long latency =
                    System.currentTimeMillis() - start;

            auditFailure(
                    requestId,
                    maskedPrompt,
                    latency,
                    aiRequest,
                    auth);

            metricsService.increment(
                    MetricsConstants.FAILED_REQUESTS);



            throw ex;

        }

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

    private void auditFailure(
            UUID requestId,
            String maskedPrompt,
            long latency,
            AIRequest aiRequest,
            AuthenticationContext auth) {

        String provider = null;
        String model = null;

        if (aiRequest != null) {

            if (aiRequest.getProvider() != null) {
                provider =
                        aiRequest.getProvider().name();
            }

            model =
                    aiRequest.getModel();
        }

        /*
         * If AIRequest was not created, fall back to the
         * authentication/tenant defaults.
         */
        if (provider == null
                && auth != null
                && auth.getDefaultProvider() != null) {

            provider =
                    auth.getDefaultProvider().name();
        }

        if (model == null
                && auth != null) {

            model =
                    auth.getDefaultModel();
        }

        auditService.save(
                requestId,
                maskedPrompt,
                null,
                latency,
                model,
                provider,
                AuditStatus.FAILED);
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

            metricsService.increment(
                    MetricsConstants.ROUTING_DECISIONS);

            switch (routingDecision.strategy()) {

                case EXPLICIT_PROVIDER ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_EXPLICIT_PROVIDER);

                case EXPLICIT_MODEL ->
                        metricsService.increment(
                                MetricsConstants.ROUTING_EXPLICIT_MODEL);

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

            metricsService.incrementProviderRequest(
                    selectedProvider);

            return AIRequest.builder()
                    .provider(selectedProvider)
                    .model(selectedModel)
                    .prompt(prompt)
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

        AIProvider provider =
                providerFactory.getProvider(
                        request.getProvider());

        return provider.chat(request);

    }

    private void persistUsage(
            UUID requestId,
            AuthenticationContext auth,
            AIRequest request,
            AIResponse response) {

        Usage usage = response.getUsage();

        if (usage == null) {
            return;
        }

        quotaService.consumeTokens(
                auth.getTenantId(),
                usage.getTotalTokens());

        tokenUsageService.save(
                requestId,
                request,
                response);

        costService.save(
                requestId,
                auth,
                request,
                response);
    }

    private String restoreResponse(
            UUID requestId,
            AIResponse response) {

        return restoreService.restore(
                response.getResponse(),
                requestId);

    }

    private void auditSuccess(
            UUID requestId,
            String prompt,
            AIRequest request,
            AIResponse response,
            long latency) {

        auditService.save(
                requestId,
                prompt,
                response.getResponse(),
                latency,
                request.getModel(),
                request.getProvider().name(),
                AuditStatus.SUCCESS);

        metricsService.addLatency(latency);

        metricsService.increment(
                MetricsConstants.SUCCESSFUL_REQUESTS);

    }
    private void validateFeature(
            AuthenticationContext auth,
            Feature feature) {

        entitlementService.validateFeature(
                auth.getTenantId(),
                feature);
    }




}


