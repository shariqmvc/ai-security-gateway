package com.ai.gateway.core.failover;

import com.ai.gateway.core.model.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Lightweight in-process circuit breaker for provider/model execution.
 *
 * It is intentionally local to the gateway instance. Durable routing health
 * remains the responsibility of RoutingHealthService; this component exists
 * to prevent repeatedly paying a long provider timeout on the request hot path.
 */
@Component
public class ProviderCircuitBreaker {

    private final ConcurrentMap<Key, State> states = new ConcurrentHashMap<>();

    private final ProviderCircuitBreakerProperties properties;
    private final Clock clock;

    @Autowired
    public ProviderCircuitBreaker(ProviderCircuitBreakerProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ProviderCircuitBreaker(
            ProviderCircuitBreakerProperties properties,
            Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns true when the provider/model can be attempted.
     *
     * An expired open circuit is treated as a half-open probe. A successful
     * probe closes the circuit; a failed probe opens it again.
     */
    public boolean allowRequest(Provider provider, String model) {
        if (!properties.isEnabled() || provider == null || model == null || model.isBlank()) {
            return true;
        }

        Key key = new Key(provider, model);
        State state = states.get(key);
        if (state == null) {
            return true;
        }

        long remaining =
                state.openUntilEpochMs - clock.millis();

        if (state.openUntilEpochMs <= 0L) {
            return true;
        }

        if (remaining > 0L) {
            return false;
        }

        /*
         * Half-open: allow exactly one probe. Other concurrent requests
         * continue to fail over rather than stampeding the recovering
         * provider.
         */
        final boolean[] admitted = {false};

        states.computeIfPresent(
                key,
                (ignored, current) -> {
                    if (current.openUntilEpochMs <= clock.millis()
                            && !current.halfOpenProbe) {

                        admitted[0] = true;

                        return new State(
                                current.consecutiveFailures,
                                current.openUntilEpochMs,
                                true,
                                current.category);
                    }

                    return current;
                });

        return admitted[0];
    }

    public long retryAfterMs(Provider provider, String model) {
        State state = states.get(new Key(provider, model));
        if (state == null) {
            return 0L;
        }

        return Math.max(
                0L,
                state.openUntilEpochMs - clock.millis());
    }

    public void recordSuccess(Provider provider, String model) {
        if (provider == null || model == null || model.isBlank()) {
            return;
        }
        states.remove(new Key(provider, model));
    }

    public void recordFailure(
            Provider provider,
            String model,
            ProviderFailureCategory category) {

        if (!properties.isEnabled()
                || provider == null
                || model == null
                || model.isBlank()
                || category == null) {
            return;
        }

        Key key = new Key(provider, model);

        states.compute(key, (ignored, previous) -> {

            int consecutiveFailures =
                    previous == null
                            ? 1
                            : previous.consecutiveFailures + 1;

            if (consecutiveFailures
                    < Math.max(1, properties.getFailureThreshold())) {

                return new State(
                        consecutiveFailures,
                        0L,
                        false,
                        category);
            }

            long openDurationMs =
                    properties.openDurationMs(category);

            return new State(
                    consecutiveFailures,
                    clock.millis() + openDurationMs,
                    false,
                    category);
        });
    }

    public void recordFailure(
            Provider provider,
            String model,
            ProviderFailureCategory category,
            long openDurationMs) {

        if (!properties.isEnabled()
                || provider == null
                || model == null
                || model.isBlank()
                || category == null) {
            return;
        }

        Key key = new Key(provider, model);

        states.compute(key, (ignored, previous) -> {

            int consecutiveFailures =
                    previous == null
                            ? 1
                            : previous.consecutiveFailures + 1;

            if (consecutiveFailures
                    < Math.max(1, properties.getFailureThreshold())) {

                return new State(
                        consecutiveFailures,
                        0L,
                        false,
                        category);
            }

            return new State(
                    consecutiveFailures,
                    clock.millis()
                            + Math.max(0L, openDurationMs),
                    false,
                    category);
        });
    }

    public int consecutiveFailures(Provider provider, String model) {
        State state = states.get(new Key(provider, model));
        return state == null ? 0 : state.consecutiveFailures;
    }

    /**
     * Non-mutating routing-path check.
     *
     * <p>Unlike {@link #allowRequest(Provider, String)}, this method never
     * consumes the half-open probe. Candidate filtering can therefore inspect
     * health before scoring without reserving a probe that may never execute.</p>
     */
    public boolean isCurrentlyOpen(Provider provider, String model) {
        if (!properties.isEnabled()
                || provider == null
                || model == null
                || model.isBlank()) {
            return false;
        }

        State state = states.get(new Key(provider, model));
        if (state == null) {
            return false;
        }

        return state.openUntilEpochMs > clock.millis();
    }

    public boolean isOpen(Provider provider, String model) {
        return !allowRequest(provider, model);
    }

    public void reset() {
        states.clear();
    }

    private record Key(Provider provider, String model) {
    }

    private record State(
            int consecutiveFailures,
            long openUntilEpochMs,
            boolean halfOpenProbe,
            ProviderFailureCategory category) {
    }
}

