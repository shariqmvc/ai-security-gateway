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

        String provider =
                aiRequest != null
                        ? aiRequest.getProvider().name()
                        : auth.getDefaultProvider().name();

        String model =
                aiRequest != null
                        ? aiRequest.getModel()
                        : auth.getDefaultModel();

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
            ChatRequest request,
            AuthenticationContext auth,
            String prompt) {



        Provider selectedProvider =
                request.getProvider() != null
                        ? request.getProvider()
                        : auth.getDefaultProvider();

        AIProvider provider =
                providerFactory.getProvider(selectedProvider);

        String selectedModel =
                request.getModel() != null
                        && !request.getModel().isBlank()
                        ? request.getModel()
                        : provider.defaultModel();

        log.info(
                "Tenant={} Provider={} Model={}",
                auth.getTenantCode(),
                selectedProvider,
                selectedModel);

        metricsService.incrementProviderRequest(
                selectedProvider);

        return AIRequest.builder()
                .provider(selectedProvider)
                .model(selectedModel)
                .prompt(prompt)
                .build();

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

    private void consumeTokenQuota(
            AuthenticationContext auth,
            AIResponse response) {

        if (response.getUsage() == null) {
            log.warn(
                    "Token usage unavailable. tenant={}",
                    auth.getTenantCode());

            return;
        }

        Integer totalTokens =
                response.getUsage()
                        .getTotalTokens();

        if (totalTokens == null ||
                totalTokens <= 0) {

            log.warn(
                    "Invalid token usage. tenant={}, totalTokens={}",
                    auth.getTenantCode(),
                    totalTokens);

            return;
        }

        quotaService.consumeTokens(
                auth.getTenantId(),
                totalTokens.longValue());
    }


}


