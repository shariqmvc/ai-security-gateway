package com.ai.gateway.core.routing.health;

import com.ai.gateway.core.failover.ProviderCircuitBreaker;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FailureAwareCandidateFilter {

    private final RoutingHealthService healthService;
    private final ProviderCircuitBreaker providerCircuitBreaker;

    /**
     * Production constructor. Durable routing health and the local circuit
     * breaker are combined into one cheap pre-scoring eligibility gate.
     */
    @Autowired
    public FailureAwareCandidateFilter(
            RoutingHealthService healthService,
            ProviderCircuitBreaker providerCircuitBreaker) {
        this.healthService = healthService;
        this.providerCircuitBreaker = providerCircuitBreaker;
    }

    /**
     * Compatibility constructor retained for existing unit-test and
     * lightweight construction paths. A disabled/null breaker means that
     * only durable routing health is evaluated.
     */
    public FailureAwareCandidateFilter(
            RoutingHealthService healthService) {
        this(healthService, null);
    }

    public List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(this::isEligible)
                .toList();
    }

    private boolean isEligible(RoutingCandidate candidate) {

        /*
         * Circuit state is an execution-time signal and therefore has
         * precedence over durable health. An OPEN circuit must prevent the
         * candidate from reaching expensive scoring/selection.
         *
         * This check is deliberately non-mutating: it does not consume the
         * half-open probe. The actual provider execution boundary calls
         * allowRequest(), which owns probe admission.
         */
        if (providerCircuitBreaker != null
                && providerCircuitBreaker.isCurrentlyOpen(
                        candidate.provider(),
                        candidate.model())) {
            return false;
        }

        return healthService == null
                || healthService.isHealthyForRouting(candidate);
    }
}
