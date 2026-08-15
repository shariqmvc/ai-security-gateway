package com.ai.gateway.routing;

import java.util.List;
import java.util.Objects;

/**
 * Diagnostic metadata produced by routing.
 *
 * <p>Metadata is intentionally additive: existing routing consumers can
 * continue using provider/model/strategy while observability and routing
 * analytics can inspect why a policy candidate was selected.</p>
 */
public record RoutingDecisionMetadata(
        Double selectedScore,
        Integer selectedRank,
        Integer candidateCount,
        String selectionReason,
        boolean extensiveResearchEnabled,
        String executionRole,
        List<RoutingCandidateMetadata> rankedCandidates) {

    public RoutingDecisionMetadata {
        if (selectedScore != null
                && (Double.isNaN(selectedScore) || Double.isInfinite(selectedScore))) {
            throw new IllegalArgumentException("Selected score must be finite.");
        }
        if (selectedRank != null && selectedRank < 1) {
            throw new IllegalArgumentException("Selected rank must be positive.");
        }
        if (candidateCount != null && candidateCount < 0) {
            throw new IllegalArgumentException("Candidate count cannot be negative.");
        }
        rankedCandidates = rankedCandidates == null
                ? List.of()
                : List.copyOf(rankedCandidates);
    }

    public static RoutingDecisionMetadata empty() {
        return new RoutingDecisionMetadata(
                null, null, null, null, false, null, List.of());
    }

    public record RoutingCandidateMetadata(
            String provider,
            String model,
            double score,
            int rank) {

        public RoutingCandidateMetadata {
            Objects.requireNonNull(provider, "Provider is required.");
            Objects.requireNonNull(model, "Model is required.");
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                throw new IllegalArgumentException("Candidate score must be finite.");
            }
            if (rank < 1) {
                throw new IllegalArgumentException("Candidate rank must be positive.");
            }
        }
    }
}
