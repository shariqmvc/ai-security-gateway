package com.ai.gateway.service;

import com.ai.gateway.observability.RequestCorrelationFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter =
            new RequestCorrelationFilter();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdAndExposeItToClient() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/chat");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String requestId = response.getHeader("X-Request-ID");
        assertNotNull(requestId);
        assertDoesNotThrow(() -> UUID.fromString(requestId));
        assertEquals(requestId, request.getAttribute("requestId"));
        assertNotNull(response.getHeader("X-Request-Latency-Ms"));
        verify(chain).doFilter(request, response);
        assertNull(MDC.get("requestId"));
    }

    @Test
    void shouldRejectInvalidIncomingRequestIdAndGenerateSafeCorrelationId() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/health");
        request.addHeader("X-Request-ID", "not-a-uuid");
        MockHttpServletResponse response =
                new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertDoesNotThrow(() -> UUID.fromString(
                response.getHeader("X-Request-ID")));
        verify(chain).doFilter(request, response);
    }
}
