package com.ai.gateway.routing.scoring;

import com.ai.gateway.routing.engine.RoutingCandidate;

/**
 * Strategy for producing one raw candidate-scoring metric.
 *
 * <p>Strategies do not select candidates and do not apply hard constraints.
 * They only provide a raw metric that the scoring engine normalizes and
 * combines using configured weights.</p>
 */
public interface CandidateScoreStrategy {

    CandidateScoreDimension dimension();

    double rawScore(
            RoutingCandidate candidate,
            CandidateScoringContext context);

    /**
     * Whether a lower raw value is better for this dimension.
     */
    default boolean lowerIsBetter() {
        return false;
    }
}
