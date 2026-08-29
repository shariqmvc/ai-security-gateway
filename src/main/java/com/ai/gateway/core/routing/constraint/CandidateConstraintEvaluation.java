package com.ai.gateway.core.routing.constraint;

import com.ai.gateway.core.routing.engine.RoutingCandidate;

import java.util.List;
import java.util.Objects;

/**
 * Complete deterministic hard-constraint evaluation for one candidate.
 */
public record CandidateConstraintEvaluation(
        RoutingCandidate candidate,
        boolean eligible,
        List<ConstraintEvaluationResult> results) {

    public CandidateConstraintEvaluation {
        Objects.requireNonNull(candidate, "Candidate is required.");
        results = results == null ? List.of() : List.copyOf(results);
    }

    public List<ConstraintEvaluationResult> failures() {
        return results.stream()
                .filter(result -> !result.passed())
                .toList();
    }
}
