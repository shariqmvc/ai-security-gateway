package com.ai.gateway.core.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerationConfig {

    /**
     * Keep the gateway's streaming responses from being cut short by a
     * provider-side default output budget. The provider still enforces the
     * model's own maximum output limit.
     */
    private Integer maxOutputTokens;
}
