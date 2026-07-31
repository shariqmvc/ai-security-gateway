package com.ai.gateway.service.impl;

import com.ai.gateway.config.GeminiConfig;
import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.dto.*;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.enums.Provider;
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
    //   private final AiProvider aiProvider;
    private final AuditService auditService;
    private final AIProviderFactory providerFactory;
    private final OpenAIConfig openAIConfig;
    private final GeminiConfig geminiConfig;

    @Override
    public ChatResponse process(ChatRequest request) {
        UUID requestId = UUID.randomUUID();
        long start = System.currentTimeMillis();

        MaskingResult maskingResult = null;
        AIRequest aiRequest = null;

        try {

            maskingResult = piiDetectionService.mask(request.getPrompt());

            tokenVaultService.save(
                    requestId,
                    maskingResult.getDetectedValues());

            String model = switch (request.getProvider()) {

                case OPENAI -> openAIConfig.getModel();

                case GEMINI -> geminiConfig.getModel();

                default -> throw new IllegalArgumentException("Unsupported provider");
            };

            aiRequest = AIRequest.builder()
                    .provider(request.getProvider())
                    .model(model)
                    .prompt(maskingResult.getMaskedPrompt())
                    .build();

            AIResponse aiResponse =
                    providerFactory
                            .getProvider(aiRequest.getProvider())
                            .chat(aiRequest);

            String restored =
                    restoreService.restore(
                            aiResponse.getResponse(),
                            requestId);

            long latency = System.currentTimeMillis() - start;

            auditService.save(
                    requestId,
                    maskingResult.getMaskedPrompt(),
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

            auditService.save(
                    requestId,
                    maskingResult != null ? maskingResult.getMaskedPrompt() : null,
                    null,
                    System.currentTimeMillis() - start,
                    aiRequest != null ? aiRequest.getModel() : null,
                    aiRequest != null ? aiRequest.getProvider().name() : null,
                    AuditStatus.FAILED);

            throw ex;
        }
    }
}

