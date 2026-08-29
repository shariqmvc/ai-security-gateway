package com.ai.gateway.core.provider;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AIStreamResult {
    String response;
    com.ai.gateway.core.model.Provider provider;
    String model;
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
    Long latencyMs;
}
