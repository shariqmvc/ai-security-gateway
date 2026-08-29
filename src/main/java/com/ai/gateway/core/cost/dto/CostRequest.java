package com.ai.gateway.core.cost.dto;

import com.ai.gateway.core.model.Provider;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CostRequest {

    private Provider provider;

    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

}