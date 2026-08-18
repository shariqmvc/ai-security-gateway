package com.ai.gateway.routing.selection;

/**
 * Structured explanation of the terminal deterministic selection step.
 */
public record CandidateSelectionExplanation(
        RoutingSelectionMode selectionMode,
        String decisionReason,
        boolean tieBreakApplied,
        String tieBreakCriterion) {

    public CandidateSelectionExplanation {
        if (selectionMode == null) {
            throw new IllegalArgumentException("Selection mode is required.");
        }
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new IllegalArgumentException("Decision reason is required.");
        }
        if (tieBreakApplied && (tieBreakCriterion == null || tieBreakCriterion.isBlank())) {
            throw new IllegalArgumentException("Tie-break criterion is required when a tie-break is applied.");
        }
    }
}
