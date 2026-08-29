package com.ai.gateway.core.cost.dto;

import com.ai.gateway.core.model.Provider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ModelPricing {

    private Provider provider;
    private String model;

    /**
     * Price per 1 Million non-cached input tokens (USD).
     */
    private BigDecimal inputPricePerMillionTokens;

    /**
     * Optional price per 1 Million cached input tokens (USD).
     * Null means the pricing source does not provide a distinct cached-input
     * price and the normal input price is used conservatively.
     */
    private BigDecimal cachedInputPricePerMillionTokens;

    /**
     * Price per 1 Million output tokens (USD).
     */
    private BigDecimal outputPricePerMillionTokens;
}
