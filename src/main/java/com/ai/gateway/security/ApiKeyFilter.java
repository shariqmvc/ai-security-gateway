package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(HEADER);

        if (apiKey == null || apiKey.isBlank()) {

            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "API Key Missing");

            return;
        }

        Optional<ApiKey> authenticatedKey =
                apiKeyService.authenticate(apiKey);

        if (authenticatedKey.isEmpty()) {

            response.sendError(
                    HttpStatus.UNAUTHORIZED.value(),
                    "Invalid API Key");

            return;
        }

        filterChain.doFilter(request, response);

    }

}