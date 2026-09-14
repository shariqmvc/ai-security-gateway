package com.ai.gateway.personal.apikey.filter;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.personal.apikey.service.PersonalApiKeyService;
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
public class PersonalApiKeyScopeFilter extends OncePerRequestFilter {
    private final PersonalApiKeyService service;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticationContext context = (AuthenticationContext) request.getAttribute(
                AuthenticationConstants.AUTH_CONTEXT);
        if (context == null || context.getAuthenticationType() != AuthenticationType.PERSONAL_API_KEY) {
            filterChain.doFilter(request, response);
            return;
        }
        String required = requiredScope(request.getServletPath());
        if (required != null && !service.hasScope(context, required)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Personal API key scope is required: " + required);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String requiredScope(String path) {
        if (path == null) return null;
        if (path.equals("/v1/chat/completions")) return "chat";
        if (path.equals("/api/models") || path.startsWith("/api/models/")) return "models";
        if (path.startsWith("/api/rag/") || path.startsWith("/api/knowledge-bases")) return "rag";
        return null;
    }
}
