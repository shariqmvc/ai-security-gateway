package com.ai.gateway.cost.dto;

import com.ai.gateway.enums.Provider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PreRequestCostEstimate {

    private Provider provider;
    private String model;
    private BigDecimal inputCost;
    private BigDecimal outputCost;
    private BigDecimal cachedInputCost;
    private BigDecimal additionalEstimatedCost;
    private BigDecimal totalEstimatedCost;

    public BigDecimal getTotalEstimatedCost() {
        return totalEstimatedCost == null ? BigDecimal.ZERO : totalEstimatedCost;
    }
}
