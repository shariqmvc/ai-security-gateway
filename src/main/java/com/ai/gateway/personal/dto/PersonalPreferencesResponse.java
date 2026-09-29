package com.ai.gateway.personal.dto;

public record PersonalPreferencesResponse(
        String defaultProvider,
        String defaultModel,
        String billingMode,
        String routingPriority
) {}
