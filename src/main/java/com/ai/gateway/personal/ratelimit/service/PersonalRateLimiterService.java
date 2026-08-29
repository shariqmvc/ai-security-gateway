package com.ai.gateway.personal.ratelimit.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.quota.config.PersonalQuotaProperties;
import com.ai.gateway.ratelimit.dto.RateLimitConfiguration;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.enums.RateLimitType;
import com.ai.gateway.ratelimit.strategy.RateLimitStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalRateLimiterService {

    private final RateLimitStrategy strategy;
    private final PersonalQuotaProperties properties;

    public RateLimitResult check(AuthenticationContext context) {
        if (context == null || !context.isPersonalPrincipal()) {
            return allowed();
        }

        if (!properties.isEnabled() || properties.getRequestsPerMinute() <= 0) {
            return allowed();
        }

        if (context.getPersonalAccountId() == null) {
            throw new IllegalArgumentException(
                    "Personal account ID is required for Personal rate limiting.");
        }

        RateLimitConfiguration configuration =
                RateLimitConfiguration.builder()
                        .type(RateLimitType.API_KEY)
                        .capacity(properties.getRequestsPerMinute())
                        .refillTokens(properties.getRequestsPerMinute())
                        .refillDurationSeconds(60)
                        .build();

        String key = "PERSONAL_ACCOUNT:" + context.getPersonalAccountId();

        return strategy.allow(key, configuration);
    }

    private RateLimitResult allowed() {
        return RateLimitResult.builder()
                .allowed(true)
                .remainingTokens(Long.MAX_VALUE)
                .retryAfterSeconds(0)
                .build();
    }
}
