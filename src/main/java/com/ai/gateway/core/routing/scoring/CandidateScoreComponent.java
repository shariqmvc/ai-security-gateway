package com.ai.gateway.core.routing.scoring;

/**
 * One normalized and weighted scoring component for a candidate.
 */
public record CandidateScoreComponent(
        CandidateScoreDimension dimension,
        double rawValue,
        double normalizedScore,
        double weight,
        double weightedScore) {

    public CandidateScoreComponent {
        if (Double.isNaN(rawValue) || Double.isInfinite(rawValue)) {
            throw new IllegalArgumentException("Raw score must be finite.");
        }
        if (Double.isNaN(normalizedScore)
                || Double.isInfinite(normalizedScore)
                || normalizedScore < 0.0
                || normalizedScore > 1.0) {
            throw new IllegalArgumentException(
                    "Normalized score must be between 0 and 1.");
        }
        if (weight < 0.0 || Double.isInfinite(weight) || Double.isNaN(weight)) {
            throw new IllegalArgumentException("Weight must be non-negative.");
        }
    }
}
