package com.ai.gateway.service.impl;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.entity.TokenUsage;
import com.ai.gateway.repository.TokenUsageRepository;
import com.ai.gateway.service.TokenUsageService;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenUsageServiceImpl
        implements TokenUsageService {

    private final TokenUsageRepository repository;
    private final TenantSchemaRoutingService
            tenantSchemaRoutingService;

    @Override
    @Transactional
    public void save(
            UUID requestId,
            AIRequest request,
            AIResponse response) {

        tenantSchemaRoutingService.useTenantSchema();

        Usage usage = response.getUsage();

        if (usage == null) {
            usage = Usage.builder()
                    .inputTokens(0)
                    .outputTokens(0)
                    .totalTokens(0)
                    .build();
        }

        TokenUsage entity =
                TokenUsage.builder()
                        .requestId(requestId)
                        .provider(request.getProvider())
                        .model(request.getModel())
                        .inputTokens(usage.getInputTokens())
                        .outputTokens(usage.getOutputTokens())
                        .totalTokens(usage.getTotalTokens())
                        .reasoningTokens(usage.getReasoningTokens())
                        .createdAt(LocalDateTime.now())
                        .build();

        repository.save(entity);
    }
}