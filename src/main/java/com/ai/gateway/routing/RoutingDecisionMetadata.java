package com.ai.gateway.routing;

import com.ai.gateway.routing.intelligence.RoutingDecisionExplanation;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;

import java.util.List;
import java.util.Objects;

/** Diagnostic metadata produced by routing. */
public record RoutingDecisionMetadata(
        Double selectedScore,
        Integer selectedRank,
        Integer candidateCount,
        String selectionReason,
        boolean extensiveResearchEnabled,
        String executionRole,
        List<RoutingCandidateMetadata> rankedCandidates,
        RoutingDecisionExplanation explanation) {

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
        rankedCandidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
    }

    public RoutingDecisionMetadata(
            Double selectedScore,
            Integer selectedRank,
            Integer candidateCount,
            String selectionReason,
            boolean extensiveResearchEnabled,
            String executionRole,
            List<RoutingCandidateMetadata> rankedCandidates) {
        this(selectedScore, selectedRank, candidateCount, selectionReason,
                extensiveResearchEnabled, executionRole, rankedCandidates, null);
    }

    public static RoutingDecisionMetadata empty() {
        return new RoutingDecisionMetadata(null, null, null, null, false, null, List.of(), null);
    }

    public record RoutingCandidateMetadata(
            String provider,
            String model,
            double score,
            int rank,
            List<RoutingScoreComponentMetadata> scoreComponents) {

        public RoutingCandidateMetadata {
            Objects.requireNonNull(provider, "Provider is required.");
            Objects.requireNonNull(model, "Model is required.");
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                throw new IllegalArgumentException("Candidate score must be finite.");
            }
            if (rank < 1) throw new IllegalArgumentException("Candidate rank must be positive.");
            scoreComponents = scoreComponents == null ? List.of() : List.copyOf(scoreComponents);
        }

        public RoutingCandidateMetadata(
                String provider, String model, double score, int rank) {
            this(provider, model, score, rank, List.of());
        }

        public static RoutingCandidateMetadata from(
                String provider, String model, double score, int rank,
                List<CandidateScoreComponent> components) {
            List<RoutingScoreComponentMetadata> metadata = components == null
                    ? List.of()
                    : components.stream()
                            .map(component -> new RoutingScoreComponentMetadata(
                                    component.dimension().name(),
                                    component.rawValue(),
                                    component.normalizedScore(),
                                    component.weight(),
                                    component.weightedScore()))
                            .toList();
            return new RoutingCandidateMetadata(provider, model, score, rank, metadata);
        }
    }

    public record RoutingScoreComponentMetadata(
            String dimension,
            double rawValue,
            double normalizedScore,
            double weight,
            double weightedScore) {

        public RoutingScoreComponentMetadata {
            Objects.requireNonNull(dimension, "Score dimension is required.");
            if (!Double.isFinite(rawValue) || !Double.isFinite(normalizedScore)
                    || !Double.isFinite(weight) || !Double.isFinite(weightedScore)) {
                throw new IllegalArgumentException("Routing score component values must be finite.");
            }
            if (normalizedScore < 0.0 || normalizedScore > 1.0) {
                throw new IllegalArgumentException("Normalized score must be between 0 and 1.");
            }
            if (weight < 0.0) {
                throw new IllegalArgumentException("Score weight must be non-negative.");
            }
        }
    }
}
