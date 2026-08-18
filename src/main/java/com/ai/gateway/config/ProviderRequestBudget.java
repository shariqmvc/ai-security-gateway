package com.ai.gateway.config;

import java.time.Duration;

/**
 * Request-scoped provider execution deadline propagated through the synchronous
 * provider call stack. The value is thread-local so no request state is shared
 * between concurrent gateway requests.
 */
public final class ProviderRequestBudget {

    private static final ThreadLocal<Long> DEADLINE_NANOS =
            new ThreadLocal<>();

    private ProviderRequestBudget() {
    }

    public static void start(Duration budget) {
        if (budget == null || budget.isNegative() || budget.isZero()) {
            DEADLINE_NANOS.remove();
            return;
        }

        long budgetNanos = budget.toNanos();
        long now = System.nanoTime();
        long deadline = now + budgetNanos;

        // Saturate on overflow rather than creating an already-expired deadline.
        if (budgetNanos > 0 && deadline < now) {
            deadline = Long.MAX_VALUE;
        }

        DEADLINE_NANOS.set(deadline);
    }

    public static boolean isActive() {
        return DEADLINE_NANOS.get() != null;
    }

    public static long remainingMillis() {
        Long deadline = DEADLINE_NANOS.get();
        if (deadline == null) {
            return Long.MAX_VALUE;
        }

        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            return 0L;
        }

        long millis = remainingNanos / 1_000_000L;
        return Math.max(1L, millis);
    }

    public static void clear() {
        DEADLINE_NANOS.remove();
    }
}
