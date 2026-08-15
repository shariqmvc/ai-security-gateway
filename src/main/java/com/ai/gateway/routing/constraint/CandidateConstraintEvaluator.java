package com.ai.gateway.routing.constraint;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;

import java.util.List;

/**
 * Evaluates deterministic hard constraints after candidate eligibility
 * filtering and before candidate scoring.
 */
public interface CandidateConstraintEvaluator {

    CandidateConstraintEvaluation evaluate(
            RoutingCandidate candidate,
            RoutingPolicy policy);

    default List<RoutingCandidate> filter(
            List<RoutingCandidate> candidates,
            RoutingPolicy policy) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> evaluate(candidate, policy).eligible())
                .distinct()
                .toList();
    }
}
