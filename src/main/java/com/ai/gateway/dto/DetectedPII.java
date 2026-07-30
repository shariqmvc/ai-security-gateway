package com.ai.gateway.dto;

import com.ai.gateway.constants.PIIType;
import lombok.*;

@Value
@Builder
public class DetectedPII {

    /**
     * Original detected value
     */
    private String originalValue;

    /**
     * Generated placeholder token
     */
    private String token;

    /**
     * EMAIL
     * PHONE
     * CREDIT_CARD
     */
    private PIIType piiType;

}
