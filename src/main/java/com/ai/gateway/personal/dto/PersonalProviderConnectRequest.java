package com.ai.gateway.personal.dto;

import com.ai.gateway.core.model.Provider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PersonalProviderConnectRequest(
        @NotNull Provider provider,
        @NotBlank @Size(max = 255) String displayName,
        @NotBlank @Size(max = 4096) String apiKey) {
}
