package com.ai.gateway.authentication;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final ApiKeyService apiKeyService;

    /**
     * Optional bootstrap credential for the first platform owner.
     * It uses the same X-API-Key authentication mechanism but is deliberately
     * not associated with a tenant. For production, prefer a managed secret.
     */
    @Value("${aegis.security.platform.bootstrap-api-key:}")
    private String platformBootstrapApiKey;

    @Override
    public AuthenticationResult authenticate(HttpServletRequest request) {

        Enumeration<String> headerValues = request.getHeaders("X-API-Key");

        List<String> apiKeys = headerValues == null
                ? List.of()
                : java.util.Collections.list(headerValues);

        if (apiKeys.isEmpty()) {
            return unauthenticated("Missing API Key");
        }

        if (apiKeys.size() != 1
                || apiKeys.get(0) == null
                || apiKeys.get(0).isBlank()) {
            return unauthenticated("Invalid API Key header");
        }

        String supplied = apiKeys.get(0).trim();

        // Tenant credentials are checked first.
        Optional<ApiKey> tenantKey = apiKeyService.authenticate(supplied);
        if (tenantKey.isPresent()) {
            return authenticateTenantKey(tenantKey.get());
        }

        // Platform bootstrap credential deliberately has no tenant context.
        if (platformBootstrapApiKey != null
                && !platformBootstrapApiKey.isBlank()
                && constantTimeEquals(platformBootstrapApiKey.trim(), supplied)) {

            AuthenticationContext context = AuthenticationContext.builder()
                    .authenticationType(AuthenticationType.API_KEY)
                    .apiKeyId(null)
                    .clientName("platform-bootstrap")
                    .role(SecurityRole.PLATFORM_OWNER)
                    .platformPrincipal(true)
                    .tenantId(null)
                    .tenantCode(null)
                    .tenantName(null)
                    .tenantType(null)
                    .defaultProvider(null)
                    .defaultModel(null)
                    .schemaName(null)
                    .build();

            return AuthenticationResult.builder()
                    .authenticated(true)
                    .context(context)
                    .build();
        }

        return unauthenticated("Invalid API Key");
    }

    private AuthenticationResult authenticateTenantKey(ApiKey key) {
        Tenant tenant = key.getTenant();

        if (tenant == null
                || tenant.getId() == null
                || tenant.getSchemaName() == null
                || tenant.getSchemaName().isBlank()) {
            return unauthenticated("Tenant is not configured for operational access");
        }

        String expectedSchema = "tenant_" + tenant.getId()
                .toString()
                .replace("-", "")
                .toLowerCase();

        if (!expectedSchema.equals(tenant.getSchemaName())) {
            return unauthenticated("Tenant schema configuration is invalid");
        }

        AuthenticationContext context = AuthenticationContext.builder()
                .authenticationType(AuthenticationType.API_KEY)
                .apiKeyId(key.getId())
                .clientName(key.getClientName())
                .tenantId(tenant.getId())
                .tenantCode(tenant.getTenantCode())
                .tenantName(tenant.getTenantName())
                .tenantType(tenant.getType())
                .defaultProvider(tenant.getDefaultProvider())
                .defaultModel(tenant.getDefaultModel())
                .schemaName(tenant.getSchemaName())
                .role(key.getRole() == null ? SecurityRole.TENANT_USER : key.getRole())
                .platformPrincipal(false)
                .build();

        return AuthenticationResult.builder()
                .authenticated(true)
                .context(context)
                .build();
    }

    private AuthenticationResult unauthenticated(String message) {
        return AuthenticationResult.builder()
                .authenticated(false)
                .message(message)
                .build();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
