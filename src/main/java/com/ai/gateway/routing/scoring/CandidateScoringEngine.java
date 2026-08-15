package com.ai.gateway.routing.scoring;

import com.ai.gateway.routing.engine.RoutingCandidate;

import java.util.List;

/**
 * Scores candidates after hard-constraint evaluation and before candidate
 * selection.
 */
public interface CandidateScoringEngine {

    List<ScoredCandidate> score(
            List<RoutingCandidate> candidates,
            CandidateScoringContext context);
}
