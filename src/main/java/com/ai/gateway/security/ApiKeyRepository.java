package com.ai.gateway.security;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.enums.ApiKeyStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("""
            SELECT ak
            FROM ApiKey ak
            JOIN FETCH ak.tenant
            WHERE ak.apiKey = :apiKey
            """)
    Optional<ApiKey> findByApiKeyWithTenant(
            @Param("apiKey") String apiKey);

    Optional<ApiKey> findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
            UUID tenantId, ApiKeyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ak
            FROM ApiKey ak
            WHERE ak.tenant.id = :tenantId
              AND ak.status = :status
            """)
    List<ApiKey> findByTenantIdAndStatusForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("status") ApiKeyStatus status);

    List<ApiKey> findByTenantIdAndStatus(UUID tenantId, ApiKeyStatus status);
}
