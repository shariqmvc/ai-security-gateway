package com.ai.gateway.ratelimit.strategy;

import com.ai.gateway.ratelimit.dto.RateLimitConfiguration;
import com.ai.gateway.ratelimit.dto.RateLimitResult;

public interface RateLimitStrategy {
    RateLimitResult allow(
            String key,
            RateLimitConfiguration configuration);
}
