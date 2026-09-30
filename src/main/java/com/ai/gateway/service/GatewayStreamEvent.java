package com.ai.gateway.service;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class GatewayStreamEvent {
    UUID requestId;
    String type;
    String content;
    String provider;
    String model;
    String requestedProvider;
    String requestedModel;
    String failoverReason;
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
    Long latencyMs;
    String error;
    String phase;
    String finishReason;
}
