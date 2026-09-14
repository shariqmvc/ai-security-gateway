package com.ai.gateway.personal.apikey.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record PersonalApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        Set<String> scopes,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        boolean active) {}
