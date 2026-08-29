package com.ai.gateway.core.cost.dto;

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