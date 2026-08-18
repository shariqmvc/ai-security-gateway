package com.ai.gateway.routing.selection;

import com.ai.gateway.routing.scoring.ScoredCandidate;

import java.util.List;

/**
 * Terminal candidate-selection contract for already scored, hard-constraint-eligible candidates.
 */
public interface CandidateSelectionEngine {

    CandidateSelectionResult selectWithRanking(List<ScoredCandidate> candidates);

    /**
     * Selects according to an explicit terminal selection mode without changing
     * upstream eligibility, optimization or scoring semantics.
     */
    CandidateSelectionResult select(
            List<ScoredCandidate> candidates,
            RoutingSelectionRequest request);

    default ScoredCandidate select(List<ScoredCandidate> candidates) {
        return selectWithRanking(candidates).selected();
    }
}
