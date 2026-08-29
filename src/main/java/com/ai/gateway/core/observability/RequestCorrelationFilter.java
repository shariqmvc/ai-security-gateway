package com.ai.gateway.core.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Establishes a request correlation id for every HTTP request and exposes it
 * to logging through MDC. The id is also returned to API clients so a request
 * can be traced across Postman, gateway logs and downstream telemetry.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        } else {
            try {
                requestId = UUID.fromString(requestId.trim()).toString();
            } catch (IllegalArgumentException ex) {
                requestId = UUID.randomUUID().toString();
            }
        }

        MDC.put(REQUEST_ID, requestId);
        request.setAttribute(REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long started = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            response.setHeader("X-Request-Latency-Ms", Long.toString(durationMs));
            MDC.remove(REQUEST_ID);
        }
    }
}
