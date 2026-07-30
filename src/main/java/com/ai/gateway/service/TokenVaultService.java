package com.ai.gateway.service;

import com.ai.gateway.dto.DetectedPII;

import java.util.List;
import java.util.UUID;

public interface TokenVaultService {
    void save(UUID requestId, List<DetectedPII> detectedValues);
}
