package com.ai.gateway.cost.dto;

import com.ai.gateway.enums.Provider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ModelPricing {

    private Provider provider;

    private String model;

    /**
     * Price per 1 Million input tokens (USD)
     */
    private BigDecimal inputPricePerMillionTokens;

    /**
     * Price per 1 Million output tokens (USD)
     */
    private BigDecimal outputPricePerMillionTokens;

}
