package com.ai.gateway.business.routing.health.repository;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.business.routing.health.entity.RoutingOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoutingOutcomeRepository extends JpaRepository<RoutingOutcome, UUID> {
    List<RoutingOutcome> findTop100ByProviderAndModelOrderByCreatedAtDesc(Provider provider, String model);
}
