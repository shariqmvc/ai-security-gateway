package com.ai.gateway.routing.constraint;

import java.util.Objects;

/**
 * Result of one deterministic hard-constraint evaluation.
 */
public record ConstraintEvaluationResult(
        HardConstraintType type,
        boolean passed,
        String reason) {

    public ConstraintEvaluationResult {
        Objects.requireNonNull(type, "Constraint type is required.");
        reason = reason == null || reason.isBlank()
                ? (passed ? "Constraint passed." : "Constraint failed.")
                : reason;
    }

    public static ConstraintEvaluationResult pass(
            HardConstraintType type,
            String reason) {
        return new ConstraintEvaluationResult(type, true, reason);
    }

    public static ConstraintEvaluationResult fail(
            HardConstraintType type,
            String reason) {
        return new ConstraintEvaluationResult(type, false, reason);
    }
}
