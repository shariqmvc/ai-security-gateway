package com.ai.gateway.core.routing.health.repository;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.health.entity.RoutingHealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutingHealthProfileRepository extends JpaRepository<RoutingHealthProfile, UUID> {
    Optional<RoutingHealthProfile> findByProviderAndModelAndEndpointId(Provider provider, String model, String endpointId);
    List<RoutingHealthProfile> findAllByOrderByUpdatedAtDesc();
}
