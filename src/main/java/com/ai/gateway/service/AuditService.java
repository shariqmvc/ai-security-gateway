package com.ai.gateway.service;

import com.ai.gateway.enums.AuditStatus;

import java.util.UUID;

public interface AuditService {
    void save(UUID requestId,
              String maskedPrompt,
              String maskedResponse,
              long latency,
              String model,
              String provider,
              AuditStatus status);

}
