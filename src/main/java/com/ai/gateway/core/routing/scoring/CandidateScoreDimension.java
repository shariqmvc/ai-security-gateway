package com.ai.gateway.core.routing.scoring;

/**
 * Configurable dimensions used to score an already hard-constraint-eligible
 * routing candidate.
 */
public enum CandidateScoreDimension {
    COST,
    LATENCY,
    AVAILABILITY,
    POLICY_PREFERENCE
}
