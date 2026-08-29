package com.ai.gateway.personal.ratelimit.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.quota.config.PersonalQuotaProperties;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.strategy.RateLimitStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PersonalRateLimiterServiceTest {

    private RateLimitStrategy strategy;
    private PersonalQuotaProperties properties;
    private PersonalRateLimiterService service;

    @BeforeEach
    void setUp() {
        strategy = mock(RateLimitStrategy.class);
        properties = new PersonalQuotaProperties();
        properties.setEnabled(true);
        properties.setRequestsPerMinute(5);
        service = new PersonalRateLimiterService(strategy, properties);
    }

    @Test
    void allowsPersonalRequestUsingAccountScopedKey() {
        UUID accountId = UUID.randomUUID();
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        when(strategy.allow(anyString(), any()))
                .thenReturn(RateLimitResult.builder()
                        .allowed(true)
                        .remainingTokens(4)
                        .retryAfterSeconds(0)
                        .build());

        RateLimitResult result = service.check(context);

        assertTrue(result.isAllowed());
        verify(strategy).allow(
                eq("PERSONAL_ACCOUNT:" + accountId),
                any());
    }

    @Test
    void rejectsWhenStrategyRejects() {
        UUID accountId = UUID.randomUUID();
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(accountId)
                .build();

        when(strategy.allow(anyString(), any()))
                .thenReturn(RateLimitResult.builder()
                        .allowed(false)
                        .retryAfterSeconds(30)
                        .build());

        RateLimitResult result = service.check(context);

        assertTrue(!result.isAllowed());
        assertEquals(30, result.getRetryAfterSeconds());
    }

    @Test
    void doesNotRateLimitWhenPersonalLimitIsNotConfigured() {
        properties.setRequestsPerMinute(0);

        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .personalAccountId(UUID.randomUUID())
                .build();

        RateLimitResult result = service.check(context);

        assertTrue(result.isAllowed());
        verifyNoInteractions(strategy);
    }

    @Test
    void bypassesNonPersonalPrincipal() {
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(false)
                .build();

        RateLimitResult result = service.check(context);

        assertTrue(result.isAllowed());
        verifyNoInteractions(strategy);
    }
}
