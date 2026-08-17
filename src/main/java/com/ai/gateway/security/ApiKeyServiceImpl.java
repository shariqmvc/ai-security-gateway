package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.enums.ApiKeyStatus;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final int SECRET_BYTES = 32;
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final ApiKeyRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public Optional<ApiKey> authenticate(String apiKey) {
        Optional<ApiKey> optional =
                repository.findByApiKeyWithTenant(apiKey);

        if (optional.isEmpty()) {
            return Optional.empty();
        }

        ApiKey entity = optional.get();

        if (entity.getStatus() != ApiKeyStatus.ACTIVE
                || entity.getTenant().getStatus() != TenantStatus.ACTIVE) {
            return Optional.empty();
        }

        if (entity.getExpiresAt() != null
                && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        entity.setLastUsedAt(LocalDateTime.now());
        return Optional.of(entity);
    }

    @Override
    @Transactional
    public ApiKeyProvisioningResult provisionInitialKey(Tenant tenant) {
        Optional<ApiKey> existing =
                repository.findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
                        tenant.getId(), ApiKeyStatus.ACTIVE);

        if (existing.isPresent()) {
            // Never return an existing secret because the raw secret is not
            // persisted and cannot be recovered safely.
            return ApiKeyProvisioningResult.from(existing.get(), null);
        }

        ApiKey key = createKey(tenant, "tenant-bootstrap");
        return ApiKeyProvisioningResult.from(key, key.getApiKey());
    }

    @Override
    @Transactional
    public ApiKeyProvisioningResult rotate(Tenant tenant, String clientName) {
        return rotate(tenant, clientName, SecurityRole.TENANT_USER);
    }

    @Override
    @Transactional
    public ApiKeyProvisioningResult rotate(
            Tenant tenant,
            String clientName,
            SecurityRole role) {

        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException(
                    "API keys can only be rotated for ACTIVE tenants.");
        }

        List<ApiKey> activeKeys = repository.findByTenantIdAndStatusForUpdate(
                tenant.getId(), ApiKeyStatus.ACTIVE);
        activeKeys.forEach(key -> key.setStatus(ApiKeyStatus.INACTIVE));
        repository.saveAll(activeKeys);

        ApiKey key = createKey(
                tenant,
                clientName == null || clientName.isBlank()
                        ? "tenant-rotation"
                        : clientName);

        return ApiKeyProvisioningResult.from(key, key.getApiKey());
    }

    @Override
    @Transactional
    public void revoke(Tenant tenant, java.util.UUID apiKeyId) {
        ApiKey key = repository.findById(apiKeyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "API key not found."));

        if (!key.getTenant().getId().equals(tenant.getId())) {
            throw new IllegalArgumentException(
                    "API key does not belong to tenant.");
        }

        key.setStatus(ApiKeyStatus.INACTIVE);
        repository.save(key);
    }

    private ApiKey createKey(Tenant tenant, String clientName) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String secret = generateSecret();
            if (repository.findByApiKeyWithTenant(secret).isPresent()) {
                continue;
            }

            ApiKey key = ApiKey.builder()
                    .apiKey(secret)
                    .clientName(clientName)
                    .tenant(tenant)
                    .status(ApiKeyStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .build();

            return repository.save(key);
        }

        throw new IllegalStateException(
                "Unable to generate a unique API key.");
    }

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return "aegis_" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
