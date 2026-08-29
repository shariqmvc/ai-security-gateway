package com.ai.gateway.tenant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

import com.ai.gateway.tenant.TenantStatus;

public interface TenantRepository
        extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByTenantCode(String tenantCode);

    long countByStatus(TenantStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t
            from Tenant t
            where t.id = :tenantId
            """)
    Optional<Tenant> findByIdForUpdate(
            @Param("tenantId") UUID tenantId);

}