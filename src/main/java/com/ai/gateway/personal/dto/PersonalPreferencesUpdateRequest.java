package com.ai.gateway.personal.dto;

import jakarta.validation.constraints.Size;

public record PersonalPreferencesUpdateRequest(
        @Size(max = 32) String defaultProvider,
        @Size(max = 160) String defaultModel,
        @Size(max = 16) String billingMode,
        @Size(max = 32) String routingPriority
) {}
