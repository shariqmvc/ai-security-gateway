package com.ai.gateway.ratelimit.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.ratelimit.dto.RateLimitResult;

public interface RateLimiterService {
    RateLimitResult check(AuthenticationContext context);

}
