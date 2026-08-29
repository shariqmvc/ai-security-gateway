package com.ai.gateway.core.routing.selection;

import com.ai.gateway.core.routing.scoring.ScoredCandidate;

import java.util.List;

public record CandidateSelectionResult(
        ScoredCandidate selected,
        List<ScoredCandidate> rankedCandidates,
        List<ScoredCandidate> selectedCandidates,
        CandidateSelectionExplanation explanation) {

    public CandidateSelectionResult {
        if (selected == null) {
            throw new IllegalArgumentException("Selected candidate is required.");
        }
        rankedCandidates = rankedCandidates == null
                ? List.of()
                : List.copyOf(rankedCandidates);
        selectedCandidates = selectedCandidates == null
                ? List.of(selected)
                : List.copyOf(selectedCandidates);
        if (selectedCandidates.isEmpty()) {
            throw new IllegalArgumentException("At least one selected candidate is required.");
        }
        if (explanation == null) {
            throw new IllegalArgumentException("Selection explanation is required.");
        }
    }

    /** Backward-compatible single-winner constructor. */
    public CandidateSelectionResult(
            ScoredCandidate selected,
            List<ScoredCandidate> rankedCandidates) {
        this(
                selected,
                rankedCandidates,
                List.of(selected),
                new CandidateSelectionExplanation(
                        RoutingSelectionMode.SINGLE,
                        "HIGHEST_UTILITY",
                        false,
                        null));
    }
}
