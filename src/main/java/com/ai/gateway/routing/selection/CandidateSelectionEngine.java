package com.ai.gateway.routing.selection;

import com.ai.gateway.routing.scoring.ScoredCandidate;

import java.util.List;

/**
 * Selects exactly one candidate from an already scored candidate set.
 * Implementations may expose the deterministic ranking together with the
 * winner so callers do not need to sort the same candidates twice.
 */
public interface CandidateSelectionEngine {

    CandidateSelectionResult selectWithRanking(List<ScoredCandidate> candidates);

    default ScoredCandidate select(List<ScoredCandidate> candidates) {
        return selectWithRanking(candidates).selected();
    }
}
