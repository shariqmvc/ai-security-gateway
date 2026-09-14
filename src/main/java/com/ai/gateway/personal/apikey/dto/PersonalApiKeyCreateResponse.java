package com.ai.gateway.personal.apikey.dto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record PersonalApiKeyCreateResponse(
        UUID id,
        String name,
        String apiKey,
        String keyPrefix,
        Set<String> scopes,
        LocalDateTime createdAt,
        LocalDateTime expiresAt) {}
