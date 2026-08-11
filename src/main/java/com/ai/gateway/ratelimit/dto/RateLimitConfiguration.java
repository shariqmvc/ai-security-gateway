package com.ai.gateway.ratelimit.dto;

import com.ai.gateway.ratelimit.enums.RateLimitType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitConfiguration {

    private RateLimitType type;

    private long capacity;

    private long refillTokens;

    private long refillDurationSeconds;

}
