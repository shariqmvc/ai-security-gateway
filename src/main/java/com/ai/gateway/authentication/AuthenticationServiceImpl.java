package com.ai.gateway.authentication;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {
    private final ApiKeyService apiKeyService;

    @Override
    public AuthenticationResult authenticate(
            HttpServletRequest request) {

        Enumeration<String> headerValues =
                request.getHeaders("X-API-Key");

        List<String> apiKeys =
                headerValues == null
                        ? List.of()
                        : java.util.Collections.list(headerValues);

        if (apiKeys.isEmpty()) {
            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Missing API Key")
                    .build();
        }

        // Reject duplicate credentials instead of relying on servlet/container
        // header ordering. This prevents ambiguous credential selection and
        // API-key header smuggling across tenant boundaries.
        if (apiKeys.size() != 1 || apiKeys.get(0) == null
                || apiKeys.get(0).isBlank()) {
            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Invalid API Key header")
                    .build();
        }

        String apiKey = apiKeys.get(0).trim();

        Optional<ApiKey> optional =
                apiKeyService.authenticate(apiKey);

        if (optional.isEmpty()) {

            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Invalid API Key")
                    .build();

        }

        ApiKey key = optional.get();

        Tenant tenant = key.getTenant();

        if (tenant == null
                || tenant.getId() == null
                || tenant.getSchemaName() == null
                || tenant.getSchemaName().isBlank()) {
            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Tenant is not configured for operational access")
                    .build();
        }

        String expectedSchema =
                "tenant_" + tenant.getId().toString()
                        .replace("-", "")
                        .toLowerCase();

        if (!expectedSchema.equals(tenant.getSchemaName())) {
            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Tenant schema configuration is invalid")
                    .build();
        }

        AuthenticationContext context =
                AuthenticationContext.builder()

                        .authenticationType(
                                AuthenticationType.API_KEY)

                        .apiKeyId(key.getId())

                        .clientName(key.getClientName())

                        .tenantId(tenant.getId())

                        .tenantCode(tenant.getTenantCode())

                        .tenantName(tenant.getTenantName())

                        .tenantType(tenant.getType())

                        .defaultProvider(
                                tenant.getDefaultProvider())

                        .defaultModel(
                                tenant.getDefaultModel())

                        .schemaName(tenant.getSchemaName())

                        .build();

        return AuthenticationResult.builder()
                .authenticated(true)
                .context(context)
                .build();

    }
}
