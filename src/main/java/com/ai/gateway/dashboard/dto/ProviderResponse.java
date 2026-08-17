package com.ai.gateway.dashboard.dto;

import com.ai.gateway.enums.Provider;

public record ProviderResponse(
        Provider provider,
        long healthyModels,
        long degradedModels,
        long unhealthyModels) {
}
