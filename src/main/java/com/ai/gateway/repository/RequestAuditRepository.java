package com.ai.gateway.repository;

import com.ai.gateway.entity.RequestAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestAuditRepository extends JpaRepository<RequestAudit, Long> {

    Optional<RequestAudit> findByRequestUuid(UUID requestUuid);

}