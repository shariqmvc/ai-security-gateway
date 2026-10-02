package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InMemoryRoutingCapacityService implements RoutingCapacityService {

    private final RoutingCapacityProperties properties;
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final Map<String, UsageWindow> usage = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRoutingCapacityService(RoutingCapacityProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemoryRoutingCapacityService() {
        this(new RoutingCapacityProperties(), Clock.systemUTC());
    }

    InMemoryRoutingCapacityService(RoutingCapacityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RoutingCapacitySignals snapshot() {
        if (!properties.isEnabled()) return RoutingCapacitySignals.empty();

        Map<String, Integer> flights = new ConcurrentHashMap<>();
        Map<String, Double> rpm = new ConcurrentHashMap<>();
        Map<String, Double> tpm = new ConcurrentHashMap<>();
        Map<String, Double> utilization = new ConcurrentHashMap<>();

        inFlight.forEach((key, value) -> flights.put(key, Math.max(0, value.get())));
        usage.forEach((key, window) -> {
            window.prune(now(), properties.getRpmWindowSeconds(), properties.getTpmWindowSeconds());
            rpm.put(key, window.requests());
            tpm.put(key, window.tokens());
        });

        flights.keySet().forEach(key -> utilization.put(key, utilization(key, flights.get(key))));
        return new RoutingCapacitySignals(flights, rpm, tpm, utilization);
    }

    @Override
    public boolean tryAcquire(RoutingCandidate candidate) {
        if (candidate == null || !properties.isEnabled()) return true;

        Integer max = maxParallel(candidate.candidateKey());
        AtomicInteger counter =
                inFlight.computeIfAbsent(candidate.candidateKey(), ignored -> new AtomicInteger());

        if (max == null || max <= 0) {
            counter.incrementAndGet();
            return true;
        }

        while (true) {
            int current = Math.max(0, counter.get());
            if (current >= max) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    @Override
    public void release(RoutingCandidate candidate) {
        if (candidate == null || !properties.isEnabled()) return;
        AtomicInteger counter = inFlight.get(candidate.candidateKey());
        if (counter != null) counter.updateAndGet(value -> Math.max(0, value - 1));
    }

    @Override
    public void recordCompletedRequest(RoutingCandidate candidate, long totalTokens) {
        if (candidate == null || !properties.isEnabled()) return;
        usage.computeIfAbsent(candidate.candidateKey(), ignored -> new UsageWindow())
                .add(now(), Math.max(0L, totalTokens));
    }

    @Override
    public int inFlight(RoutingCandidate candidate) {
        if (candidate == null || !properties.isEnabled()) return 0;
        AtomicInteger counter = inFlight.get(candidate.candidateKey());
        return counter == null ? 0 : Math.max(0, counter.get());
    }

    @Override
    public double utilization(RoutingCandidate candidate) {
        if (candidate == null || !properties.isEnabled()) return 0.0;
        return utilization(candidate.candidateKey(), inFlight(candidate));
    }

    @Override
    public boolean isAtHardLimit(RoutingCandidate candidate) {
        if (candidate == null || !properties.isEnabled() || !properties.isHardLimit()) return false;
        Integer max = maxParallel(candidate.candidateKey());
        return max != null && max > 0 && inFlight(candidate) >= max;
    }

    private double utilization(String key, int current) {
        Integer max = maxParallel(key);
        return max == null || max <= 0
                ? 0.0
                : Math.min(1.0, current / (double) max);
    }

    private Integer maxParallel(String key) {
        Integer exact = properties.getMaxParallel().get(key);
        if (exact != null) return exact;
        int separator = key.indexOf('@');
        if (separator > 0) {
            Integer providerModel = properties.getMaxParallel().get(key.substring(0, separator));
            if (providerModel != null) return providerModel;
        }
        return properties.getMaxParallelRequests();
    }

    private Instant now() {
        return clock.instant();
    }

    private static final class UsageWindow {
        private final Deque<UsageEvent> events = new ArrayDeque<>();

        synchronized void add(Instant timestamp, long tokens) {
            events.addLast(new UsageEvent(timestamp, tokens));
        }

        synchronized void prune(Instant now, int rpmSeconds, int tpmSeconds) {
            int seconds = Math.max(rpmSeconds, tpmSeconds);
            Instant cutoff = now.minusSeconds(Math.max(1, seconds));
            while (!events.isEmpty() && events.peekFirst().timestamp().isBefore(cutoff)) {
                events.removeFirst();
            }
        }

        synchronized double requests() {
            return events.size();
        }

        synchronized double tokens() {
            return events.stream().mapToLong(UsageEvent::tokens).sum();
        }
    }

    private record UsageEvent(Instant timestamp, long tokens) {}
}
