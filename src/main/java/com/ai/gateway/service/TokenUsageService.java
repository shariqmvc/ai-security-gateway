package com.ai.gateway.service;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;

import java.util.UUID;

public interface TokenUsageService {
    void save(UUID requestId,
              AIRequest request,
              AIResponse response);

}
