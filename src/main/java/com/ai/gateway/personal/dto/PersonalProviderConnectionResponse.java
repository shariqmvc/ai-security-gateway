package com.ai.gateway.personal.dto;

import com.ai.gateway.core.model.Provider;

import java.time.LocalDateTime;
import java.util.UUID;

public record PersonalProviderConnectionResponse(
        UUID id,
        Provider provider,
        String displayName,
        String status,
        String maskedCredential,
        LocalDateTime lastValidatedAt,
        String validationMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
