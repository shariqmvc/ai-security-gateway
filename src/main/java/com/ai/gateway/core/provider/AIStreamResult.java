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
    String finishReason;

    /** Provider requested by the caller before streaming failover, when failover occurred. */
    com.ai.gateway.core.model.Provider failoverFromProvider;

    /** Classified reason for the primary provider failure that triggered failover. */
    String failoverReason;
}
