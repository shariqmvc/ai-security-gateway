package com.ai.gateway.routing.health.repository;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.health.entity.RoutingOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoutingOutcomeRepository extends JpaRepository<RoutingOutcome, UUID> {
    List<RoutingOutcome> findTop100ByProviderAndModelOrderByCreatedAtDesc(Provider provider, String model);
}
