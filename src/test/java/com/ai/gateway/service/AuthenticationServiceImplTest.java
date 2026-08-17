package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationResult;
import com.ai.gateway.authentication.AuthenticationServiceImpl;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AuthenticationServiceImplTest {

    private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
    private final AuthenticationServiceImpl service =
            new AuthenticationServiceImpl(apiKeyService);

    @Test
    void shouldRejectDuplicateApiKeyHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-API-Key"))
                .thenReturn(java.util.Collections.enumeration(
                        List.of("aegis_key_a", "aegis_key_b")));

        AuthenticationResult result = service.authenticate(request);

        assertFalse(result.isAuthenticated());
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectTenantWithMismatchedSchemaIdentity() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .tenantCode("TEST")
                .tenantName("Test")
                .status(TenantStatus.ACTIVE)
                .schemaName("tenant_some_other_tenant")
                .build();

        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .apiKey("aegis_key")
                .clientName("test")
                .tenant(tenant)
                .build();

        when(apiKeyService.authenticate("aegis_key"))
                .thenReturn(Optional.of(apiKey));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-API-Key"))
                .thenReturn(java.util.Collections.enumeration(
                        List.of("aegis_key")));

        AuthenticationResult result = service.authenticate(request);

        assertFalse(result.isAuthenticated());
    }

    @Test
    void shouldAuthenticateSingleValidApiKey() {
        UUID tenantId = UUID.randomUUID();
        String schema = "tenant_" + tenantId.toString().replace("-", "").toLowerCase();

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .tenantCode("TEST")
                .tenantName("Test")
                .status(TenantStatus.ACTIVE)
                .schemaName(schema)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID())
                .apiKey("aegis_key")
                .clientName("test")
                .tenant(tenant)
                .build();

        when(apiKeyService.authenticate("aegis_key"))
                .thenReturn(Optional.of(apiKey));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-API-Key"))
                .thenReturn(java.util.Collections.enumeration(
                        List.of(" aegis_key ")));

        AuthenticationResult result = service.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertTrue(result.getContext().getTenantId().equals(tenantId));
    }
}
