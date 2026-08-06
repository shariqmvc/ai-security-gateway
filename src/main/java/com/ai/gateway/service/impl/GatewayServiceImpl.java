package com.ai.gateway.service.impl;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.*;
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

    private final AuditService auditService;

    private final AIProviderFactory providerFactory;

    private final PromptFireWallService firewallService;

    private final PolicyEngineService policyEngineService;

    private final GatewayMetricsService metricsService;

    private final TokenUsageService tokenUsageService;

    @Override
    public ChatResponse process(ChatRequest request) {

        metricsService.increment(MetricsConstants.TOTAL_REQUESTS);

        UUID requestId = UUID.randomUUID();

        long start = System.currentTimeMillis();

        String originalPrompt = request.getPrompt();

        String maskedPrompt = originalPrompt;




        AIRequest aiRequest = null;
        AuthenticationContext auth = getAuthenticationContext();


        try {


            // -------------------------------
            // Prompt Firewall
            // -------------------------------

            FirewallResult firewall =
                    firewallService.inspect(originalPrompt);

            if (!firewall.isAllowed()) {

                metricsService.increment(
                        MetricsConstants.FIREWALL_BLOCKED);

                throw new BusinessException(
                        firewall.getReason());

            }

            // -------------------------------
            // Policy Engine
            // -------------------------------

            PolicyResult policy =
                    policyEngineService.evaluate(originalPrompt);

            if (!policy.isAllowed()) {

                metricsService.increment(
                        MetricsConstants.POLICY_BLOCKED);

                throw new BusinessException(
                        policy.getReason());

            }

            // -------------------------------
            // PII Detection
            // -------------------------------

            MaskingResult maskingResult =
                    piiDetectionService.mask(originalPrompt);

            maskedPrompt =
                    maskingResult.getMaskedPrompt();

            // -------------------------------
            // Token Vault
            // -------------------------------

            tokenVaultService.save(
                    requestId,
                    maskingResult.getDetectedValues());

            // -------------------------------
            // AI Request
            // -------------------------------

            Provider selectedProvider =
                    request.getProvider() != null
                            ? request.getProvider()
                            : auth.getDefaultProvider();

            AIProvider aiProvider =
                    providerFactory.getProvider(selectedProvider);

            String selectedModel =
                    request.getModel() != null && !request.getModel().isBlank()
                            ? request.getModel()
                            : aiProvider.defaultModel();

            aiRequest =
                    AIRequest.builder()
                            .provider(selectedProvider)
                            .model(selectedModel)
                            .prompt(maskedPrompt)
                            .build();
            log.info(
                    "Processing request. tenant={}, provider={}, model={}",
                    auth.getTenantCode(),
                    selectedProvider,
                    selectedModel);
            metricsService.incrementProviderRequest(selectedProvider);

            // -------------------------------
            // Provider Invocation
            // -------------------------------

            AIResponse aiResponse =
                    aiProvider .chat(aiRequest);

            // -------------------------------
            // Token Usage
            // -------------------------------

            tokenUsageService.save(
                    requestId,
                    aiRequest,
                    aiResponse);

            // -------------------------------
            // Restore Response
            // -------------------------------

            String restored =
                    restoreService.restore(
                            aiResponse.getResponse(),
                            requestId);

            long latency =
                    System.currentTimeMillis() - start;

            metricsService.addLatency(latency);

            // -------------------------------
            // Audit
            // -------------------------------

            auditService.save(
                    requestId,
                    maskedPrompt,
                    aiResponse.getResponse(),
                    latency,
                    aiRequest.getModel(),
                    aiRequest.getProvider().name(),
                    AuditStatus.SUCCESS);

            metricsService.increment(
                    MetricsConstants.SUCCESSFUL_REQUESTS);

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



    }


