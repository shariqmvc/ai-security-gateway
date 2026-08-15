package com.ai.gateway.routing.scoring;

import com.ai.gateway.routing.engine.RoutingCandidate;

import java.util.List;
import java.util.Objects;

/**
 * Complete deterministic score for one candidate.
 */
public record ScoredCandidate(
        RoutingCandidate candidate,
        List<CandidateScoreComponent> components,
        double totalScore) {

    public ScoredCandidate {
        Objects.requireNonNull(candidate, "Candidate is required.");
        components = components == null ? List.of() : List.copyOf(components);
        if (Double.isNaN(totalScore) || Double.isInfinite(totalScore)) {
            throw new IllegalArgumentException("Total score must be finite.");
        }
    }
}
