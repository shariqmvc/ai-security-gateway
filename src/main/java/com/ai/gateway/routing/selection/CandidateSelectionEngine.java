package com.ai.gateway.routing.selection;

import com.ai.gateway.routing.scoring.ScoredCandidate;

import java.util.List;

/**
 * Selects exactly one candidate from an already scored candidate set.
 *
 * <p>Selection never evaluates hard constraints. Candidates reaching this
 * contract are assumed to have passed 6.5.4.</p>
 */
public interface CandidateSelectionEngine {

    ScoredCandidate select(List<ScoredCandidate> candidates);
}
