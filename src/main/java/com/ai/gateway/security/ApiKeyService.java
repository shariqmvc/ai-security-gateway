package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;

import java.util.Optional;

public interface ApiKeyService {
    Optional<ApiKey> authenticate(String apiKey);


}
