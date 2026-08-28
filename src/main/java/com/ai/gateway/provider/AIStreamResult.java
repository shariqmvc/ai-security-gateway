package com.ai.gateway.provider;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AIStreamResult {
    String response;
    com.ai.gateway.enums.Provider provider;
    String model;
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
    Long latencyMs;
}
