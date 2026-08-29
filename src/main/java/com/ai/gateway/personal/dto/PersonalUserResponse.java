package com.ai.gateway.personal.dto;

import java.util.UUID;

public record PersonalUserResponse(
        UUID userId,
        UUID accountId,
        String email,
        String displayName,
        String plan,
        String status,
        boolean emailVerified) {
}
