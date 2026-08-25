package com.ai.gateway.business.repository;

import com.ai.gateway.business.Business;
import com.ai.gateway.business.BusinessStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findByBusinessId(UUID businessId);

    Optional<Business> findByTenant_Id(UUID tenantId);

    long countByBusinessStatus(BusinessStatus status);
}
