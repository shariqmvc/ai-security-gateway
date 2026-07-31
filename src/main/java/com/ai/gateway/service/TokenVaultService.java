package com.ai.gateway.service;

import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.entity.TokenVault;

import java.util.List;
import java.util.UUID;

public interface TokenVaultService {
    void save(UUID requestId, List<DetectedPII> detectedValues);
    List<TokenVault> getTokens(UUID requestId);
}
