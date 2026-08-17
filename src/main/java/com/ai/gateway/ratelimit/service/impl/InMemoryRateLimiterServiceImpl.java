package com.ai.gateway.ratelimit.service.impl;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.ratelimit.config.RateLimitConstants;
import com.ai.gateway.ratelimit.dto.RateLimitConfiguration;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.enums.RateLimitType;
import com.ai.gateway.ratelimit.service.RateLimiterService;
import com.ai.gateway.ratelimit.strategy.RateLimitStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InMemoryRateLimiterServiceImpl
        implements RateLimiterService {

    private final RateLimitStrategy strategy;

    private final EntitlementService entitlementService;

    @Override
    public RateLimitResult check(
            AuthenticationContext context) {

        if (context == null || context.isPlatformPrincipal()) {
            return RateLimitResult.builder()
                    .allowed(true)
                    .retryAfterSeconds(0)
                    .build();
        }

        if (context.getTenantId() == null) {
            throw new IllegalArgumentException(
                    "Tenant ID is required for tenant rate limiting.");
        }

        String key =
                buildKey(context);

        TenantEntitlementDto entitlement =
                entitlementService.getDto(
                        context.getTenantId());

        RateLimitConfiguration configuration =
                buildConfiguration(entitlement);

        return strategy.allow(
                key,
                configuration);
    }

    private String buildKey(
            AuthenticationContext context) {

        return RateLimitType.API_KEY.name()
                + ":"
                + context.getApiKeyId();
    }

    private RateLimitConfiguration buildConfiguration(
            TenantEntitlementDto entitlement) {

        return RateLimitConfiguration.builder()
                .type(RateLimitType.API_KEY)
                .capacity(
                        entitlement.getRequestsPerMinute())
                .refillTokens(
                        entitlement.getRequestsPerMinute())
                .refillDurationSeconds(60)
                .build();
    }
}