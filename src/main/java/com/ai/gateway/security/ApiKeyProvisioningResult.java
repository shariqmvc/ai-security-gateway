package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApiKeyProvisioningResult(
        UUID id,
        UUID tenantId,
        String clientName,
        String apiKey,
        String status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {

    public static ApiKeyProvisioningResult from(ApiKey key, String secret) {
        return new ApiKeyProvisioningResult(
                key.getId(),
                key.getTenant().getId(),
                key.getClientName(),
                secret,
                key.getStatus().name(),
                key.getCreatedAt(),
                key.getExpiresAt());
    }
}
