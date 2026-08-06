package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository
        extends JpaRepository<ApiKey, UUID> {

    @Query("""
            SELECT ak
            FROM ApiKey ak
            JOIN FETCH ak.tenant
            WHERE ak.apiKey = :apiKey
            """)
    Optional<ApiKey> findByApiKeyWithTenant(
            @Param("apiKey") String apiKey);

}
