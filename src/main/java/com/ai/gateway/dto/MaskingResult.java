package com.ai.gateway.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaskingResult {

    /**
     * Prompt after replacing PII with tokens
     */
    private String maskedPrompt;

    /**
     * All detected PII values
     */
    private List<DetectedPII> detectedValues;

}
