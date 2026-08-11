package com.ai.gateway.entitlement.security;

import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthenticationContextResolver {

    public AuthenticationContext resolve() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException(
                    "No HTTP request context available.");
        }

        HttpServletRequest request =
                attributes.getRequest();

        AuthenticationContext context =
                (AuthenticationContext)
                        request.getAttribute(
                                AuthenticationConstants.AUTH_CONTEXT);

        if (context == null) {
            throw new IllegalStateException(
                    "AuthenticationContext not found.");
        }

        return context;
    }
}