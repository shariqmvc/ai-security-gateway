package com.ai.gateway.routing.scoring.objective;

/** Inclusive finite bounds for one objective's raw metric. */
public record RoutingObjectiveBounds(double minimum, double maximum) {

    public RoutingObjectiveBounds {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum)) {
            throw new IllegalArgumentException("Objective bounds must be finite.");
        }
        if (minimum > maximum) {
            throw new IllegalArgumentException("Objective minimum cannot exceed maximum.");
        }
    }

    public boolean isConstant() {
        return Double.compare(minimum, maximum) == 0;
    }
}
