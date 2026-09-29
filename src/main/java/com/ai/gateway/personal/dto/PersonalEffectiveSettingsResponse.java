package com.ai.gateway.personal.dto;

import java.util.List;
import java.util.Set;

public record PersonalEffectiveSettingsResponse(
        Account account,
        Limits limits,
        Set<String> features,
        Set<String> freeModels
) {
    public record Account(
            String plan,
            String status
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
