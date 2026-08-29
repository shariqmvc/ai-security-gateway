package com.ai.gateway.personal.ratelimit.filter;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.ratelimit.service.PersonalRateLimiterService;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PersonalRateLimitFilterTest {

    private PersonalRateLimiterService service;
    private FilterChain chain;
    private PersonalRateLimitFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        service = mock(PersonalRateLimiterService.class);
        chain = mock(FilterChain.class);
        filter = new PersonalRateLimitFilter(service);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void allowsPersonalRequest() throws Exception {
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .build();
        request.setAttribute(AuthenticationConstants.AUTH_CONTEXT, context);

        when(service.check(context)).thenReturn(
                RateLimitResult.builder().allowed(true).build());

        filter.doFilter(request, response, chain);

        verify(service).check(context);
        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsPersonalRequestWhenLimited() throws Exception {
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(true)
                .build();
        request.setAttribute(AuthenticationConstants.AUTH_CONTEXT, context);

        when(service.check(context)).thenReturn(
                RateLimitResult.builder()
                        .allowed(false)
                        .retryAfterSeconds(30)
                        .build());

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        verify(service).check(context);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void bypassesNonPersonalRequest() throws Exception {
        AuthenticationContext context = AuthenticationContext.builder()
                .personalPrincipal(false)
                .build();
        request.setAttribute(AuthenticationConstants.AUTH_CONTEXT, context);

        filter.doFilter(request, response, chain);

        verifyNoInteractions(service);
        verify(chain).doFilter(request, response);
    }
}
