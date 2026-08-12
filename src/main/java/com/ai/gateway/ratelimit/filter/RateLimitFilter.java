package com.ai.gateway.ratelimit.filter;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.quota.exception.QuotaExceededException;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class RateLimitFilter
        extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final QuotaService quotaService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        AuthenticationContext context =
                (AuthenticationContext)
                        request.getAttribute(
                                AuthenticationConstants.AUTH_CONTEXT);

        if (context == null) {

            filterChain.doFilter(request, response);

            return;

        }

        RateLimitResult result =
                rateLimiterService.check(context);

        if (!result.isAllowed()) {

            response.setStatus(
                    HttpStatus.TOO_MANY_REQUESTS.value());

            response.setContentType("application/json");

            response.getWriter().write(
                    """
                    {
                        "message":"Rate limit exceeded",
                        "retryAfter":%d
                    }
                    """.formatted(
                            result.getRetryAfterSeconds()));

            return;

        }
        /*
         * ---------------------------------------------------------
         * 2. Daily request quota
         * ---------------------------------------------------------
         */

        try {

            quotaService.consumeRequest(
                    context.getTenantId());

        } catch (QuotaExceededException ex) {

            response.setStatus(
                    HttpStatus.TOO_MANY_REQUESTS.value());

            response.setContentType(
                    "application/json");

            response.getWriter().write(
                    """
                    {
                        "message":"Daily request quota exceeded"
                    }
                    """);

            return;
        }

        /*
         * ---------------------------------------------------------
         * 3. Continue request
         * ---------------------------------------------------------
         */


        filterChain.doFilter(
                request,
                response);

    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

}