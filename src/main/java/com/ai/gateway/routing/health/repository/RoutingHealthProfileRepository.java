package com.ai.gateway.routing.health.repository;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.health.entity.RoutingHealthProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutingHealthProfileRepository extends JpaRepository<RoutingHealthProfile, UUID> {
    Optional<RoutingHealthProfile> findByProviderAndModel(Provider provider, String model);
    List<RoutingHealthProfile> findAllByOrderByUpdatedAtDesc();
}
