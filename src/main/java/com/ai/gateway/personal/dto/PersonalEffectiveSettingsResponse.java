package com.ai.gateway.personal.dto;

import java.util.Set;

public record PersonalEffectiveSettingsResponse(
        Account account,
        Preferences preferences,
        Limits limits,
        Set<String> features,
        Set<String> freeModels
) {
    public record Account(
            String plan,
            String status
    ) {}

    public record Preferences(
            String defaultProvider,
            String defaultModel,
            String billingMode,
            String routingPriority
    ) {}

    public record Limits(
            boolean quotaEnabled,
            long requestsPerMinute,
            long requestsPerDay,
            long monthlyTokenQuota,
            long maxInputTokens,
            long maxOutputTokens,
            long maxConcurrentRequests
    ) {}
}
