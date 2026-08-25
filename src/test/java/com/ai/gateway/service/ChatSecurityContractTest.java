package com.ai.gateway.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression contract for chat authentication.
 *
 * The gateway's AuthenticationFilter is the mandatory API-key boundary for
 * /api/chat. Spring's request authorization must not impose a second role
 * decision that can turn a valid tenant API-key request into an unexplained
 * 403.
 */
class ChatSecurityContractTest {

    @Test
    void chatIsHandledByApiKeyFilterAndNotSpringRoleAuthorization() throws Exception {
        Path config = Path.of("src/main/java/com/ai/gateway/config/SecurityConfig.java");
        String source = Files.readString(config);

        assertTrue(source.contains(".requestMatchers("/api/chat").permitAll()"),
                "Chat must be delegated to the mandatory X-API-Key authentication filter.");

        Path filter = Path.of("src/main/java/com/ai/gateway/authentication/AuthenticationFilter.java");
        String filterSource = Files.readString(filter);

        assertFalse(filterSource.contains("servletPath.equals("/api/chat")"),
                "AuthenticationFilter must not bypass /api/chat.");
    }
}
