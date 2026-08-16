package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.tenant.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyService {

    Optional<ApiKey> authenticate(String apiKey);

    ApiKeyProvisioningResult provisionInitialKey(Tenant tenant);

    ApiKeyProvisioningResult rotate(Tenant tenant, String clientName);

    void revoke(Tenant tenant, UUID apiKeyId);
}
