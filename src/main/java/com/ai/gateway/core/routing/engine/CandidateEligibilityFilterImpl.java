package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.health.RoutingHealthService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class CandidateEligibilityFilterImpl
        implements CandidateEligibilityFilter {

    private final RoutingHealthService routingHealthService;

    public CandidateEligibilityFilterImpl(RoutingHealthService routingHealthService) {
        this.routingHealthService = routingHealthService;
    }

    public CandidateEligibilityFilterImpl() {
        this.routingHealthService = null;
    }

    @Override
    public List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates,
            RoutingPolicy policy) {

        if (policy == null || !policy.enabled()) {
            return List.of();
        }

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(candidate ->
                        candidate.provider() != null)
                .filter(candidate ->
                        candidate.model() != null
                                && !candidate.model().isBlank())
                .filter(candidate ->
                        policy.allowsProvider(
                                candidate.provider()))
                .filter(candidate ->
                        policy.allowsModel(
                                candidate.model()))
                .filter(candidate ->
                        routingHealthService == null
                                || routingHealthService.isHealthyForRouting(candidate))
                .distinct()
                .toList();
    }
}