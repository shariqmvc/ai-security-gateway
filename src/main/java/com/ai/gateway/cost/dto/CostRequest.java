package com.ai.gateway.cost.dto;

import com.ai.gateway.enums.Provider;
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