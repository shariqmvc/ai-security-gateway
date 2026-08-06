package com.ai.gateway.cost.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CostResponse {

    private BigDecimal inputCost;

    private BigDecimal outputCost;

    private BigDecimal totalCost;

}