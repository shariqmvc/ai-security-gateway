package com.ai.gateway.core.cost.config;

import com.ai.gateway.core.cost.dto.ModelPricing;
import com.ai.gateway.core.model.Provider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PricingConfig {

    public ModelPricing getPricing(
            Provider provider,
            String model) {

        return switch (provider) {

            case OPENAI -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(
                            new BigDecimal("1.25"))
                    .outputPricePerMillionTokens(
                            new BigDecimal("10.00"))
                    .build();

            case GEMINI -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(
                            new BigDecimal("0.30"))
                    .outputPricePerMillionTokens(
                            new BigDecimal("2.50"))
                    .build();

            case OLLAMA -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(
                            BigDecimal.ZERO)
                    .outputPricePerMillionTokens(
                            BigDecimal.ZERO)
                    .build();

            case CLAUDE -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(
                            new BigDecimal("3.00"))
                    .outputPricePerMillionTokens(
                            new BigDecimal("15.00"))
                    .build();

            case XAI -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(
                            new BigDecimal("2.00"))
                    .outputPricePerMillionTokens(
                            new BigDecimal("6.00"))
                    .build();

            case MISTRAL -> ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(new BigDecimal("0.15"))
                    .outputPricePerMillionTokens(new BigDecimal("0.60"))
                    .build();

            case GROQ -> {
                // Groq pricing varies by model. Use the current GPT-OSS
                // 120B default as the provider-level fallback.
                if ("openai/gpt-oss-20b".equalsIgnoreCase(model)) {
                    yield ModelPricing.builder()
                            .provider(provider)
                            .model(model)
                            .inputPricePerMillionTokens(
                                    new BigDecimal("0.075"))
                            .outputPricePerMillionTokens(
                                    new BigDecimal("0.30"))
                            .build();
                }

                yield ModelPricing.builder()
                        .provider(provider)
                        .model(model)
                        .inputPricePerMillionTokens(
                                new BigDecimal("0.15"))
                        .outputPricePerMillionTokens(
                                new BigDecimal("0.60"))
                        .build();
            }
        };
    }
}
