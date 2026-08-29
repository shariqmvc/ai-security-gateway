package com.ai.gateway.personal.ratelimit.filter;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.ratelimit.service.PersonalRateLimiterService;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@ConditionalOnBean(PersonalRateLimiterService.class)
@RequiredArgsConstructor
public class PersonalRateLimitFilter extends OncePerRequestFilter {

    private final PersonalRateLimiterService rateLimiterService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        AuthenticationContext context =
                (AuthenticationContext) request.getAttribute(
                        AuthenticationConstants.AUTH_CONTEXT);

        if (context == null || !context.isPersonalPrincipal()) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitResult result = rateLimiterService.check(context);

        if (!result.isAllowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    """
                    {
                        "message":"Personal rate limit exceeded",
                        "retryAfter":%d
                    }
                    """.formatted(result.getRetryAfterSeconds()));
            return;
        }

        filterChain.doFilter(request, response);
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
