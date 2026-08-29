package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.health.RoutingHealthService;
import com.ai.gateway.core.routing.scoring.config.RoutingScoringProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime signal facade. Production state is backed by the persistent 6.7
 * health service; the one-argument constructor preserves lightweight unit tests.
 */
@Service
public class RoutingRuntimeSignalService {

    private final RoutingScoringProperties properties;
    private final Map<String, SignalState> fallbackStates = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private RoutingHealthService healthService;

    public RoutingRuntimeSignalService(RoutingScoringProperties properties) {
        this.properties = properties;
    }

    public RoutingRuntimeSignals snapshot() {
        if (healthService != null) {
            Map<String, Double> latency = new ConcurrentHashMap<>();
            Map<String, Double> availability = new ConcurrentHashMap<>();
            healthService.snapshots().stream()
                    .filter(s -> s.fresh())
                    .forEach(s -> {
                        String key = s.provider().name() + ":" + s.model();
                        latency.put(key, s.ewmaLatencyMs());
                        availability.put(key, s.availability());
                    });
            return new RoutingRuntimeSignals(latency, availability);
        }

        Map<String, Double> latency = new ConcurrentHashMap<>();
        Map<String, Double> availability = new ConcurrentHashMap<>();
        fallbackStates.forEach((key, state) -> {
            latency.put(key, state.latencyMs);
            availability.put(key, state.availability());
        });
        return new RoutingRuntimeSignals(latency, availability);
    }

    public void recordSuccess(RoutingCandidate candidate, long latencyMs) {
        if (candidate == null) return;
        if (healthService != null) {
            healthService.recordSuccess(candidate, latencyMs);
            return;
        }
        fallbackStates.compute(key(candidate), (ignored, old) -> {
            SignalState state = old == null ? initial(candidate) : old;
            state.observeLatency(Math.max(0L, latencyMs));
            state.successes++;
            return state;
        });
    }

    public void recordFailure(RoutingCandidate candidate) {
        recordFailure(candidate, "PROVIDER_FAILURE");
    }

    public void recordFailure(RoutingCandidate candidate, String failureCategory) {
        if (candidate == null) return;
        if (healthService != null) {
            healthService.recordFailure(candidate, failureCategory);
            return;
        }
        fallbackStates.compute(key(candidate), (ignored, old) -> {
            SignalState state = old == null ? initial(candidate) : old;
            state.failures++;
            return state;
        });
    }

    public double currentLatency(RoutingCandidate candidate) {
        if (healthService != null) {
            var snapshot = healthService.snapshot(candidate);
            if (snapshot != null && snapshot.fresh() && snapshot.ewmaLatencyMs() > 0) {
                return snapshot.ewmaLatencyMs();
            }
        }
        SignalState state = fallbackStates.get(key(candidate));
        return state == null
                ? properties.getLatencyMs().getOrDefault(key(candidate), properties.getDefaults().getLatencyMs())
                : state.latencyMs;
    }

    public double currentAvailability(RoutingCandidate candidate) {
        if (healthService != null) {
            var snapshot = healthService.snapshot(candidate);
            if (snapshot != null && snapshot.fresh()) return snapshot.availability();
        }
        SignalState state = fallbackStates.get(key(candidate));
        return state == null
                ? properties.getAvailability().getOrDefault(key(candidate), properties.getDefaults().getAvailability())
                : state.availability();
    }

    private SignalState initial(RoutingCandidate candidate) {
        return new SignalState(
                properties.getLatencyMs().getOrDefault(key(candidate), properties.getDefaults().getLatencyMs()),
                properties.getAvailability().getOrDefault(key(candidate), properties.getDefaults().getAvailability()));
    }

    private String key(RoutingCandidate candidate) {
        return candidate.provider().name() + ":" + candidate.model();
    }

    private static final class SignalState {
        private double latencyMs;
        private final double baselineAvailability;
        private long successes;
        private long failures;

        private SignalState(double latencyMs, double baselineAvailability) {
            this.latencyMs = latencyMs;
            this.baselineAvailability = Math.max(0.0, Math.min(1.0, baselineAvailability));
        }

        private void observeLatency(long value) {
            latencyMs = latencyMs * 0.8 + value * 0.2;
        }

        private double availability() {
            long observations = successes + failures;
            if (observations == 0) return baselineAvailability;
            return Math.max(0.0, Math.min(1.0, (successes + baselineAvailability) / (observations + 1.0)));
        }
    }
}
