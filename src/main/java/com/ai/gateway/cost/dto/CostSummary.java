package com.ai.gateway.cost.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CostSummary {
    private BigDecimal totalCost;

    private BigDecimal inputCost;

    private BigDecimal outputCost;

    private Long totalRequests;

    private Long totalInputTokens;

    private Long totalOutputTokens;
}
