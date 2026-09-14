package com.ai.gateway.personal.apikey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record PersonalApiKeyCreateRequest(
        @NotBlank @Size(max = 100) String name,
        Set<String> scopes,
        @Positive @Max(3650) Integer expiresInDays) {}
