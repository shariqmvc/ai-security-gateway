package com.ai.gateway.ratelimit.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenBucket {

    /**
     * Current available tokens.
     */
    private long availableTokens;

    /**
     * Last refill time (epoch millis).
     */
    private long lastRefillTimestamp;

}
