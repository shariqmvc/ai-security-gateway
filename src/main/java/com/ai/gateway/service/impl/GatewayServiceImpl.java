package com.ai.gateway.service.impl;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GatewayServiceImpl implements GatewayService {
        private final PIIDetectionService piiDetectionService;
        private final TokenVaultService tokenVaultService;
        private final RestoreService restoreService;
        private final OpenAIService openAIService;

    @Override
    public ChatResponse process(ChatRequest request) {
        UUID requestId = UUID.randomUUID();

        MaskingResult maskingResult =
                piiDetectionService.mask(request.getPrompt());

        tokenVaultService.save(
                requestId,
                maskingResult.getDetectedValues());

        String llmResponse =
                openAIService.ask(
                        maskingResult.getMaskedPrompt());

        String restored =
                restoreService.restore(
                        llmResponse,
                        requestId);

        return ChatResponse.builder()
                .requestId(requestId)
                .response(restored)
                .build();
    }
    }

