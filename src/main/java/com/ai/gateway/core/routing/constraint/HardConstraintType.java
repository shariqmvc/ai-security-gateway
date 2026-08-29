package com.ai.gateway.core.routing.constraint;

/**
 * Deterministic hard constraints evaluated before candidate scoring.
 */
public enum HardConstraintType {
    CANDIDATE_VALID,
    PROVIDER_ALLOWED_BY_POLICY,
    MODEL_ALLOWED_BY_POLICY,
    PROVIDER_ENABLED,
    MODEL_REGISTERED,
    MODEL_ENABLED
}
