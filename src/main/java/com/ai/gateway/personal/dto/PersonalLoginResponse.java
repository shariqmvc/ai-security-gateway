package com.ai.gateway.personal.dto;

public record PersonalLoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        PersonalUserResponse user) {
}
