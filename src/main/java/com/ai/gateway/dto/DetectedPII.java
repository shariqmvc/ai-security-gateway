package com.ai.gateway.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private String piiType;

}
