package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository
        extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByApiKey(String apiKey);

}
