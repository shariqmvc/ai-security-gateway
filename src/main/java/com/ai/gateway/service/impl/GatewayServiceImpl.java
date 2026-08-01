package com.ai.gateway.service.impl;

import com.ai.gateway.config.GeminiConfig;
import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.dto.*;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.service.PromptFireWallService;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final OpenAIConfig openAIConfig;
    private final GeminiConfig geminiConfig;
    private final PromptFireWallService firewallService;

    @Override
    public ChatResponse process(ChatRequest request) {

        UUID requestId = UUID.randomUUID();
        long start = System.currentTimeMillis();

        String originalPrompt = request.getPrompt();
        String maskedPrompt = originalPrompt;

        MaskingResult maskingResult = null;
        AIRequest aiRequest = null;

        try {

            // Step 1 : Firewall
            FirewallResult firewall = firewallService.inspect(originalPrompt);

            if (!firewall.isAllowed()) {
                throw new BusinessException(firewall.getReason());
            }

            // Step 2 : Mask PII
            maskingResult = piiDetectionService.mask(originalPrompt);

            maskedPrompt = maskingResult.getMaskedPrompt();

            // Step 3 : Save Token Vault
            tokenVaultService.save(
                    requestId,
                    maskingResult.getDetectedValues());

            // Step 4 : Resolve Model
            String model = switch (request.getProvider()) {

                case OPENAI -> openAIConfig.getModel();

                case GEMINI -> geminiConfig.getModel();

                default -> throw new IllegalArgumentException(
                        "Unsupported provider");
            };

            // Step 5 : Build AI Request
            aiRequest = AIRequest.builder()
                    .provider(request.getProvider())
                    .model(model)
                    .prompt(maskedPrompt)
                    .build();

            // Step 6 : Invoke Provider
            AIResponse aiResponse =
                    providerFactory
                            .getProvider(aiRequest.getProvider())
                            .chat(aiRequest);

            // Step 7 : Restore PII
            String restored =
                    restoreService.restore(
                            aiResponse.getResponse(),
                            requestId);

            long latency = System.currentTimeMillis() - start;

            // Step 8 : Audit Success
            auditService.save(
                    requestId,
                    maskedPrompt,
                    aiResponse.getResponse(),
                    latency,
                    aiRequest.getModel(),
                    aiRequest.getProvider().name(),
                    AuditStatus.SUCCESS);

            return ChatResponse.builder()
                    .requestId(requestId)
                    .response(restored)
                    .build();

        } catch (Exception ex) {

            long latency = System.currentTimeMillis() - start;

            auditService.save(
                    requestId,
                    maskedPrompt,
                    null,
                    latency,
                    aiRequest != null ? aiRequest.getModel() : request.getProvider().name(),
                    aiRequest != null ? aiRequest.getProvider().name() : request.getProvider().name(),
                    AuditStatus.FAILED);

            throw ex;
        }
    }
}

