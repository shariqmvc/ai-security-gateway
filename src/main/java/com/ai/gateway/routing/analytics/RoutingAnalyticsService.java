package com.ai.gateway.routing.analytics;

import com.ai.gateway.routing.RoutingDecision;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RoutingAnalyticsService {

    private final AtomicLong totalDecisions = new AtomicLong();

    private final Map<String, AtomicLong> decisionsByStrategy =
            new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> decisionsByProvider =
            new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> decisionsByProviderModel =
            new ConcurrentHashMap<>();

    private final AtomicLong failoverAttempts =
            new AtomicLong();

    private final AtomicLong failoverSuccesses =
            new AtomicLong();

    private final AtomicLong failoverFailures =
            new AtomicLong();

    public void recordDecision(RoutingDecision decision) {

        if (decision == null) {
            return;
        }

        totalDecisions.incrementAndGet();

        if (decision.strategy() != null) {
            decisionsByStrategy
                    .computeIfAbsent(
                            decision.strategy().name(),
                            key -> new AtomicLong())
                    .incrementAndGet();
        }

        if (decision.provider() != null) {
            decisionsByProvider
                    .computeIfAbsent(
                            decision.provider().name(),
                            key -> new AtomicLong())
                    .incrementAndGet();
        }

        if (decision.provider() != null
                && decision.model() != null
                && !decision.model().isBlank()) {

            String key =
                    decision.provider().name()
                            + ":"
                            + decision.model();

            decisionsByProviderModel
                    .computeIfAbsent(
                            key,
                            ignored -> new AtomicLong())
                    .incrementAndGet();
        }
    }

    public void recordFailoverAttempt() {
        failoverAttempts.incrementAndGet();
    }

    public void recordFailoverSuccess() {
        failoverSuccesses.incrementAndGet();
    }

    public void recordFailoverFailure() {
        failoverFailures.incrementAndGet();
    }

    public RoutingAnalytics getAnalytics() {

        return new RoutingAnalytics(
                totalDecisions.get(),
                snapshot(decisionsByStrategy),
                snapshot(decisionsByProvider),
                snapshot(decisionsByProviderModel),
                failoverAttempts.get(),
                failoverSuccesses.get(),
                failoverFailures.get()
        );
    }

    private Map<String, Long> snapshot(
            Map<String, AtomicLong> source) {

        Map<String, Long> snapshot = new HashMap<>();

        source.forEach(
                (key, value) ->
                        snapshot.put(
                                key,
                                value.get()));

        return Map.copyOf(snapshot);
    }
}
