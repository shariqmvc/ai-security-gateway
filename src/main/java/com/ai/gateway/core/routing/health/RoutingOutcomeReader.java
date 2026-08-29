package com.ai.gateway.core.routing.health;

import com.ai.gateway.core.model.Provider;

import java.util.List;

/**
 * Product-neutral read boundary for routing observations.
 * Implementations may persist observations in Business, Personal, or another
 * product store without making the Core depend on product/domain packages.
 */
public interface RoutingOutcomeReader {
    List<RoutingOutcomeSample> findRecent(Provider provider, String model);
}
