package com.ai.gateway.personal.dto;

import jakarta.validation.constraints.NotBlank;

public record PersonalVerifyEmailRequest(
        @NotBlank String token) {
}
