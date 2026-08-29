package com.ai.gateway.core.routing.scoring.objective;

/**
 * Canonical multi-objective dimensions used by intelligent routing.
 *
 * <p>These objectives are deliberately separate from the current
 * {@code CandidateScoreDimension} implementation. B.1 defines the stable
 * objective vocabulary and normalization contract without changing the
 * existing routing score semantics.</p>
 */
public enum RoutingObjective {
    COST,
    LATENCY,
    QUALITY,
    RELIABILITY,
    CAPABILITY_FIT,
    POLICY_PREFERENCE,
    HEALTH,
    AVAILABILITY
}
