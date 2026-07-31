package com.ai.gateway.service.impl;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.dto.MaskingResult;
import com.ai.gateway.enums.AuditStatus;
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
        private final AiProvider aiProvider;
        private final AuditService auditService;

    @Override
    public ChatResponse process(ChatRequest request) {
        UUID requestId = UUID.randomUUID();
        log.info(
                "AI request started. requestId={}, provider={}, model={}",
                requestId,
                "OPENAI",
                "gpt-5");

        long start = System.currentTimeMillis();
        long latency = 0l;
        MaskingResult maskingResult = null;
        String restored = null;

      try {
          maskingResult =
                  piiDetectionService.mask(request.getPrompt());

          log.debug(
                  "Request masked. requestId={}, piiDetected={}, maskedPrompt={}",
                  requestId,
                  maskingResult.getDetectedValues().size(),
                  maskingResult.getMaskedPrompt());

          tokenVaultService.save(
                  requestId,
                  maskingResult.getDetectedValues());

          String llmResponse =
                  aiProvider.chat(maskingResult.getMaskedPrompt());

          log.info("LLM Response = {}", llmResponse);

          restored =
                  restoreService.restore(
                          llmResponse,
                          requestId);
          log.info("Restored Response = {}", restored);

          latency = System.currentTimeMillis() - start;
          auditService.save(
                  requestId,
                  maskingResult.getMaskedPrompt(),
                  llmResponse,
                  latency,
                  "gpt-5",
                  "OPENAI",
                  AuditStatus.SUCCESS
          );
          log.info(
                  "AI request completed. requestId={}, latency={} ms",
                  requestId,
                  latency);
      }catch (Exception exc) {

          log.error("Gateway processing failed", exc);

          auditService.save(
                  requestId,
                  maskingResult != null ? maskingResult.getMaskedPrompt() : null,
                  null,
                  System.currentTimeMillis() - start,
                  "gpt-5",
                  "OPENAI",
                  AuditStatus.FAILED
          );

          throw exc;
      }

        return ChatResponse.builder()
                .requestId(requestId)
                .response(restored)
                .build();
    }
    }

