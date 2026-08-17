package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationFilter;
import com.ai.gateway.authentication.AuthenticationResult;
import com.ai.gateway.authentication.AuthenticationService;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationFilterContextLifecycleTest {

    @AfterEach
    void cleanup() {
        TenantSchemaContext.clear();
        TenantContext.clear();
    }

    @Test
    void shouldClearTenantContextsAfterSuccessfulRequest() throws Exception {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authenticationService);

        UUID tenantId = UUID.randomUUID();
        String schema = "tenant_" + tenantId.toString().replace("-", "");

        AuthenticationContext context = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.API_KEY)
                .tenantId(tenantId)
                .schemaName(schema)
                .build();

        when(authenticationService.authenticate(any()))
                .thenReturn(AuthenticationResult.builder()
                        .authenticated(true)
                        .context(context)
                        .build());

        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/chat");

        doAnswer(invocation -> {
            assertEquals(tenantId, TenantContext.require());
            assertEquals(schema, TenantSchemaContext.require());
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(
                request,
                mock(HttpServletResponse.class),
                chain);

        assertNull(TenantContext.get(),
                "Tenant context must not leak after request completion.");
        assertNull(TenantSchemaContext.get(),
                "Tenant schema context must not leak after request completion.");
    }

    @Test
    void shouldClearTenantContextsWhenDownstreamRequestFails() throws Exception {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authenticationService);

        UUID tenantId = UUID.randomUUID();
        String schema = "tenant_" + tenantId.toString().replace("-", "");

        AuthenticationContext context = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.API_KEY)
                .tenantId(tenantId)
                .schemaName(schema)
                .build();

        when(authenticationService.authenticate(any()))
                .thenReturn(AuthenticationResult.builder()
                        .authenticated(true)
                        .context(context)
                        .build());

        FilterChain chain = mock(FilterChain.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/chat");
        doThrow(new ServletException("downstream failure"))
                .when(chain)
                .doFilter(any(), any());

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(
                        request,
                        mock(HttpServletResponse.class),
                        chain));

        assertNull(TenantContext.get());
        assertNull(TenantSchemaContext.get());
    }

    @Test
    void shouldNotInitializeTenantContextsWhenAuthenticationFails()
            throws IOException, ServletException {
        AuthenticationService authenticationService = mock(AuthenticationService.class);
        AuthenticationFilter filter = new AuthenticationFilter(authenticationService);

        when(authenticationService.authenticate(any()))
                .thenReturn(AuthenticationResult.builder()
                        .authenticated(false)
                        .message("Invalid API Key")
                        .build());

        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/chat");

        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(
                request,
                response,
                chain);

        verify(response).sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid API Key");

        verifyNoInteractions(chain);

        assertNull(TenantContext.get());
        assertNull(TenantSchemaContext.get());
    }
}
