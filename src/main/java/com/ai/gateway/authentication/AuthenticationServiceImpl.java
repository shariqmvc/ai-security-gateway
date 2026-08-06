package com.ai.gateway.authentication;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {
    private final ApiKeyService apiKeyService;

    @Override
    public AuthenticationResult authenticate(
            HttpServletRequest request) {

        String apiKey =
                request.getHeader("X-API-Key");

        if (apiKey == null || apiKey.isBlank()) {

            return AuthenticationResult.builder()
                    .authenticated(false)
                    .message("Missing API Key")
                    .build();

        }

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

                        .build();

        return AuthenticationResult.builder()
                .authenticated(true)
                .context(context)
                .build();

    }
}
