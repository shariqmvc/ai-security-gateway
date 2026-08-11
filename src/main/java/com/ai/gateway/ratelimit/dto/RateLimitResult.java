package com.ai.gateway.ratelimit.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {

    private boolean allowed;

    private String message;

    /**
     * Remaining tokens after this request.
     */
    private long remainingTokens;

    /**
     * Seconds until another request is allowed.
     */
    private long retryAfterSeconds;

}