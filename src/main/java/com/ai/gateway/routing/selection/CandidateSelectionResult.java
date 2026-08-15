package com.ai.gateway.routing.selection;

import com.ai.gateway.routing.scoring.ScoredCandidate;

import java.util.List;

public record CandidateSelectionResult(
        ScoredCandidate selected,
        List<ScoredCandidate> rankedCandidates) {

    public CandidateSelectionResult {
        if (selected == null) {
            throw new IllegalArgumentException("Selected candidate is required.");
        }
        rankedCandidates = rankedCandidates == null
                ? List.of()
                : List.copyOf(rankedCandidates);
    }
}
