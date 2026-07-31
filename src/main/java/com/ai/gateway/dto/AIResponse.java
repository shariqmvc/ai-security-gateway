package com.ai.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIResponse {

    private String response;

    private String providerRequestId;

    private Integer inputTokens;

    private Integer outputTokens;

}