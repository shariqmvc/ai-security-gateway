package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory runtime signal store. Signals are advisory scoring inputs, not hard constraints. */
@Service
@RequiredArgsConstructor
public class RoutingRuntimeSignalService {

    private final RoutingScoringProperties properties;
    private final Map<String, SignalState> states = new ConcurrentHashMap<>();

    public RoutingRuntimeSignals snapshot() {
        Map<String, Double> latency = new ConcurrentHashMap<>();
        Map<String, Double> availability = new ConcurrentHashMap<>();
        states.forEach((key, state) -> {
            latency.put(key, state.latencyMs);
            availability.put(key, state.availability());
        });
        return new RoutingRuntimeSignals(latency, availability);
    }

    public void recordSuccess(RoutingCandidate candidate, long latencyMs) {
        if (candidate == null) return;
        String key = key(candidate);
        states.compute(key, (ignored, old) -> {
            SignalState state = old == null ? initial(candidate) : old;
            state.observeLatency(Math.max(0L, latencyMs));
            state.successes++;
            return state;
        });
    }

    public void recordFailure(RoutingCandidate candidate) {
        if (candidate == null) return;
        states.compute(key(candidate), (ignored, old) -> {
            SignalState state = old == null ? initial(candidate) : old;
            state.failures++;
            return state;
        });
    }

    public double currentLatency(RoutingCandidate candidate) {
        SignalState state = states.get(key(candidate));
        return state == null
                ? properties.getLatencyMs().getOrDefault(key(candidate), properties.getDefaults().getLatencyMs())
                : state.latencyMs;
    }

    public double currentAvailability(RoutingCandidate candidate) {
        SignalState state = states.get(key(candidate));
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
