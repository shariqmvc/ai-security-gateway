package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationResult;
import com.ai.gateway.authentication.AuthenticationServiceImpl;
import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.personal.PersonalAuthService;
import com.ai.gateway.personal.apikey.service.PersonalApiKeyService;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

  //  private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
  //  private final AuthenticationServiceImpl service =
    //        new AuthenticationServiceImpl(apiKeyService);

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private PersonalAuthService personalAuthService;

    @Mock
    private PersonalApiKeyService personalApiKeyService;

    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationServiceImpl(
                apiKeyService,
                personalAuthService,
                personalApiKeyService);
    }

    @Test
    void shouldAuthenticatePersonalDeveloperApiKeyFromBearerHeader() {
        AuthenticationContext context = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_API_KEY)
                .personalPrincipal(true)
                .personalAccountId(UUID.randomUUID())
                .build();
        when(personalApiKeyService.authenticate("arpk_test"))
                .thenReturn(context);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization"))
                .thenReturn("Bearer arpk_test");

        AuthenticationResult result = service.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertTrue(result.getContext().isPersonalPrincipal());
        assertEquals(AuthenticationType.PERSONAL_API_KEY, result.getContext().getAuthenticationType());
        verify(personalApiKeyService).authenticate("arpk_test");
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldAuthenticatePersonalDeveloperApiKeyFromXApiKeyHeader() {
        AuthenticationContext context = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.PERSONAL_API_KEY)
                .personalPrincipal(true)
                .personalAccountId(UUID.randomUUID())
                .personalApiKeyScopes(java.util.Set.of("chat", "models"))
                .build();
        when(personalApiKeyService.authenticate("arpk_test"))
                .thenReturn(context);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-API-Key"))
                .thenReturn(java.util.Collections.enumeration(List.of("arpk_test")));

        AuthenticationResult result = service.authenticate(request);

        assertTrue(result.isAuthenticated());
        assertTrue(result.getContext().isPersonalPrincipal());
        assertEquals(AuthenticationType.PERSONAL_API_KEY, result.getContext().getAuthenticationType());
        assertTrue(result.getContext().getPersonalApiKeyScopes().contains("models"));
        verify(personalApiKeyService).authenticate("arpk_test");
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectInvalidPersonalDeveloperApiKeyFromXApiKeyHeader() {
        when(personalApiKeyService.authenticate("arpk_invalid"))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("invalid"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeaders("X-API-Key"))
                .thenReturn(java.util.Collections.enumeration(List.of("arpk_invalid")));

        AuthenticationResult result = service.authenticate(request);

        assertFalse(result.isAuthenticated());
        verify(personalApiKeyService).authenticate("arpk_invalid");
        verifyNoInteractions(apiKeyService);
    }

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
