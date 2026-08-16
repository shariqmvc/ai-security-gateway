package com.ai.gateway.authentication;

import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

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

        AuthenticationContext context =
                result.getContext();

        TenantContext.set(context.getTenantId());
        TenantSchemaContext.set(context.getSchemaName());

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        context,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_USER")));

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        request.setAttribute(
                AuthenticationConstants.AUTH_CONTEXT,
                context);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantSchemaContext.clear();
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        return request.getServletPath()
                .startsWith("/admin/");
    }
}
