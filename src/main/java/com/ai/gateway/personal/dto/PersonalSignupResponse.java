package com.ai.gateway.personal.dto;

import java.util.UUID;

public record PersonalSignupResponse(
        UUID userId,
        UUID accountId,
        String email,
        String displayName,
        String phoneNumber,
        String plan,
        String status,
        boolean emailVerified,
        boolean emailVerificationRequired,
        String verificationToken,
        boolean phoneVerificationRequired,
        String phoneVerificationCode) {
}
