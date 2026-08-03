package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.enums.ApiKeyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository repository;

    @Override
    public Optional<ApiKey> authenticate(String apiKey) {

        Optional<ApiKey> optionalApiKey =
                repository.findByApiKey(apiKey);

        if (optionalApiKey.isEmpty()) {
            return Optional.empty();
        }

        ApiKey key = optionalApiKey.get();

        if (key.getStatus() != ApiKeyStatus.ACTIVE) {
            return Optional.empty();
        }

        if (key.getExpiresAt() != null &&
                key.getExpiresAt().isBefore(LocalDateTime.now())) {

            return Optional.empty();
        }

        key.setLastUsedAt(LocalDateTime.now());

        repository.save(key);

        return Optional.of(key);
    }

}
