package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.config.GatewayAsyncConfig;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GatewayAsyncConfigSecurityContextTest {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void cleanup() {
        if (executor != null) {
            executor.shutdown();
        }
        TenantSchemaContext.clear();
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPropagateAuthenticatedPrincipalAndTenantContextToAsyncWorker()
            throws Exception {

        UUID tenantId = UUID.randomUUID();
        String schemaName = "tenant_" + tenantId.toString().replace("-", "");

        AuthenticationContext authenticationContext =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("ASYNC-TEST")
                        .schemaName(schemaName)
                        .role(SecurityRole.TENANT_OWNER)
                        .platformPrincipal(false)
                        .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        authenticationContext,
                        null,
                        List.of());

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        TenantContext.set(tenantId);
        TenantSchemaContext.set(tenantId, schemaName);

        GatewayAsyncConfig configuration =
                new GatewayAsyncConfig();

        executor = (ThreadPoolTaskExecutor)
                configuration.gatewayAsyncExecutor();

        Future<AsyncContextSnapshot> future =
                executor.submit(() -> {
                    AuthorizationService authorizationService =
                            new AuthorizationService();

                    AuthenticationContext resolved =
                            authorizationService.requireContext();

                    return new AsyncContextSnapshot(
                            resolved,
                            TenantContext.get(),
                            TenantSchemaContext.get(),
                            TenantSchemaContext.getTenantId());
                });

        AsyncContextSnapshot snapshot =
                future.get(5, TimeUnit.SECONDS);

        assertSame(
                authenticationContext,
                snapshot.authenticationContext());

        assertEquals(
                tenantId,
                snapshot.tenantId());

        assertEquals(
                schemaName,
                snapshot.schema());

        assertEquals(
                tenantId,
                snapshot.schemaTenantId());
    }

    private record AsyncContextSnapshot(
            AuthenticationContext authenticationContext,
            UUID tenantId,
            String schema,
            UUID schemaTenantId) {
    }
}
