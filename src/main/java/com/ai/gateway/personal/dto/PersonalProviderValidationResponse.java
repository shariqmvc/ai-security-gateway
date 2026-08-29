package com.ai.gateway.personal.dto;

import com.ai.gateway.core.model.Provider;

import java.time.LocalDateTime;

public record PersonalProviderValidationResponse(
        Provider provider,
        boolean valid,
        String status,
        String message,
        LocalDateTime validatedAt) {
}
