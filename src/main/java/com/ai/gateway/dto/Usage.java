package com.ai.gateway.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Usage {

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private Long latencyMs;

    private Integer reasoningTokens;

}