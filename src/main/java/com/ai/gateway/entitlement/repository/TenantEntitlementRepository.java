package com.ai.gateway.entitlement.repository;

import com.ai.gateway.entitlement.entity.TenantEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantEntitlementRepository
        extends JpaRepository<TenantEntitlement, UUID> {

    Optional<TenantEntitlement> findByTenantId(UUID tenantId);

}
