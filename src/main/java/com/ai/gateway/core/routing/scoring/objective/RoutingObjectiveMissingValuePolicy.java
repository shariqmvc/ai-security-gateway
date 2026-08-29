package com.ai.gateway.core.routing.scoring.objective;

/** Explicit policy for an objective whose raw value is unavailable. */
public enum RoutingObjectiveMissingValuePolicy {
    /** Treat an unavailable objective as neutral rather than rewarding it. */
    MIDPOINT,
    /** Treat an unavailable objective as the lowest possible normalized value. */
    ZERO,
    /** Fail fast when a required objective is unavailable. */
    REJECT
}
