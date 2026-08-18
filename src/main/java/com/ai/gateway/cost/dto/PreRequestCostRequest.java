package com.ai.gateway.cost.dto;

import com.ai.gateway.enums.Provider;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PreRequestCostRequest {

    private Provider provider;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer cachedInputTokens;

    /**
     * Optional estimated incremental provider cost for dimensions that are
     * not represented by token pricing, such as known tool/multimodal charges.
     * Unknown charges must remain null rather than being fabricated.
     */
    private BigDecimal additionalEstimatedCost;

    public void validate() {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model is required.");
        }
        validateTokens(inputTokens, "Input tokens");
        validateTokens(outputTokens, "Output tokens");
        validateTokens(cachedInputTokens, "Cached input tokens");

        int input = inputTokens == null ? 0 : inputTokens;
        int cached = cachedInputTokens == null ? 0 : cachedInputTokens;
        if (cached > input) {
            throw new IllegalArgumentException(
                    "Cached input tokens cannot exceed input tokens.");
        }

        if (additionalEstimatedCost != null
                && additionalEstimatedCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Additional estimated cost cannot be negative.");
        }
    }

    private static void validateTokens(Integer value, String label) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
    }
}
