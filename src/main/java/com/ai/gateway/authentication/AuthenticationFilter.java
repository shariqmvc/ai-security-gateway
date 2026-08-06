package com.ai.gateway.authentication;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter
        extends OncePerRequestFilter {

    private final AuthenticationService authenticationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        AuthenticationResult result =
                authenticationService.authenticate(request);

        if (!result.isAuthenticated()) {

            response.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    result.getMessage());

            return;

        }

        request.setAttribute(
                AuthenticationConstants.AUTH_CONTEXT,
                result.getContext());

        filterChain.doFilter(
                request,
                response);

    }

}