package com.ai.gateway.routing.health;

import com.ai.gateway.routing.engine.RoutingCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FailureAwareCandidateFilter {

    private final RoutingHealthService healthService;

    public FailureAwareCandidateFilter(RoutingHealthService healthService) {
        this.healthService = healthService;
    }

    public List<RoutingCandidate> filter(List<RoutingCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(candidate -> healthService.isHealthyForRouting(candidate))
                .toList();
    }
}
