package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.filter.RateLimitFilter;
import com.ai.gateway.ratelimit.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private QuotaService quotaService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private AuthenticationContext context;

    private RateLimitFilter filter;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {

        filter =
                new RateLimitFilter(
                        rateLimiterService,
                        quotaService);

        request =
                new MockHttpServletRequest();

        response =
                new MockHttpServletResponse();

        request.setAttribute(
                AuthenticationConstants.AUTH_CONTEXT,
                context);
    }

    @Test
    void shouldContinueWhenRateLimitAndQuotaAreAllowed()
            throws Exception {

        RateLimitResult result =
                RateLimitResult.builder()
                        .allowed(true)
                        .retryAfterSeconds(0)
                        .build();

        when(rateLimiterService.check(context))
                .thenReturn(result);

        filter.doFilter(
                request,
                response,
                filterChain);

        verify(rateLimiterService)
                .check(context);

        verify(quotaService)
                .consumeRequest(
                        any());

        verify(filterChain)
                .doFilter(
                        request,
                        response);

        assertEquals(
                200,
                response.getStatus());
    }

    @Test
    void shouldRejectWhenRateLimitExceeded()
            throws Exception {

        RateLimitResult result =
                RateLimitResult.builder()
                        .allowed(false)
                        .retryAfterSeconds(30)
                        .build();

        when(rateLimiterService.check(context))
                .thenReturn(result);

        filter.doFilter(
                request,
                response,
                filterChain);

        assertEquals(
                429,
                response.getStatus());

        verify(rateLimiterService)
                .check(context);

        verify(quotaService, never())
                .consumeRequest(any());

        verify(filterChain, never())
                .doFilter(
                        request,
                        response);
    }

    @Test
    void shouldRejectWhenDailyQuotaExceeded()
            throws Exception {

        RateLimitResult result =
                RateLimitResult.builder()
                        .allowed(true)
                        .retryAfterSeconds(0)
                        .build();

        when(rateLimiterService.check(context))
                .thenReturn(result);

        doThrow(
                new QuotaExceededException(
                        "Daily request quota exceeded."))
                .when(quotaService)
                .consumeRequest(any());

        filter.doFilter(
                request,
                response,
                filterChain);

        assertEquals(
                429,
                response.getStatus());

        verify(rateLimiterService)
                .check(context);

        verify(quotaService)
                .consumeRequest(any());

        verify(filterChain, never())
                .doFilter(
                        request,
                        response);
    }
}
