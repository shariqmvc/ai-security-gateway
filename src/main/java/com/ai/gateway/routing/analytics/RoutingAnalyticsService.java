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

    private final AtomicLong intelligentDecisions = new AtomicLong();
    private final AtomicLong unityDecisions = new AtomicLong();
    private final Map<String, AtomicLong> decisionsByPriority = new ConcurrentHashMap<>();

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

        if (decision.metadata() != null) {
            if (decision.metadata().explanation() != null) {
                intelligentDecisions.incrementAndGet();
            }
            if (decision.metadata().extensiveResearchEnabled()) {
                unityDecisions.incrementAndGet();
            }
            if (decision.metadata().explanation() != null) {
                decision.metadata().explanation().appliedSignals().stream()
                        .filter(signal -> signal.startsWith("routing-priority:"))
                        .findFirst()
                        .ifPresent(signal -> decisionsByPriority
                                .computeIfAbsent(signal.substring("routing-priority:".length()), k -> new AtomicLong())
                                .incrementAndGet());
            }
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
                failoverFailures.get(),
                intelligentDecisions.get(),
                unityDecisions.get(),
                snapshot(decisionsByPriority)
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
