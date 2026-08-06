package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.enums.ApiKeyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository repository;

    @Override
    @Transactional
    public Optional<ApiKey> authenticate(
            String apiKey) {

        Optional<ApiKey> optional =
                repository.findByApiKeyWithTenant(apiKey);

        if (optional.isEmpty()) {
            return Optional.empty();
        }

        ApiKey entity = optional.get();

        if (entity.getStatus() != ApiKeyStatus.ACTIVE) {
            return Optional.empty();
        }

        if (entity.getExpiresAt() != null
                && entity.getExpiresAt().isBefore(LocalDateTime.now())) {

            return Optional.empty();
        }

        entity.setLastUsedAt(LocalDateTime.now());

        return Optional.of(entity);

    }

}
