package com.ai.gateway.service;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;

import java.util.UUID;

public interface TokenUsageService {
    void save(UUID requestId,
              AIRequest request,
              AIResponse response);

}
