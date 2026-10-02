package com.ai.gateway.core.routing.telemetry;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded operational telemetry for routing executions.
 *
 * <p>This is intentionally product-neutral and in-process. It provides
 * immediate LLMOps visibility without making Core depend on Business or
 * Personal persistence. A durable/exportable sink can subscribe to the same
 * event contract in a later phase.</p>
 */
@Service
public class RoutingTelemetryService {

    private static final int MAX_RECENT_EVENTS = 200;

    private final AtomicLong totalExecutions = new AtomicLong();
    private final AtomicLong successfulExecutions = new AtomicLong();
    private final AtomicLong failedExecutions = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();

    private final Map<String, AtomicLong> executionsByCandidate =
            new ConcurrentHashMap<>();

    private final Map<String, AtomicLong> failuresByCategory =
            new ConcurrentHashMap<>();

    private final ConcurrentLinkedDeque<RoutingTelemetryEvent> recentEvents =
            new ConcurrentLinkedDeque<>();

    public void record(RoutingTelemetryEvent event) {
        if (event == null || event.provider() == null || event.model() == null) {
            return;
        }

        totalExecutions.incrementAndGet();
        totalLatencyMs.addAndGet(event.latencyMs());

        if (event.success()) {
            successfulExecutions.incrementAndGet();
        } else {
            failedExecutions.incrementAndGet();
            String category = event.failureCategory() == null
                    || event.failureCategory().isBlank()
                    ? "UNKNOWN"
                    : event.failureCategory();
            failuresByCategory
                    .computeIfAbsent(category, ignored -> new AtomicLong())
                    .incrementAndGet();
        }

        executionsByCandidate
                .computeIfAbsent(event.candidateKey(), ignored -> new AtomicLong())
                .incrementAndGet();

        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.pollLast();
        }
    }

    public RoutingTelemetrySnapshot snapshot() {
        long total = totalExecutions.get();
        long latency = totalLatencyMs.get();

        return new RoutingTelemetrySnapshot(
                total,
                successfulExecutions.get(),
                failedExecutions.get(),
                latency,
                total == 0 ? 0.0 : ((double) latency / total),
                snapshotCounts(executionsByCandidate),
                snapshotCounts(failuresByCategory),
                new ArrayList<>(recentEvents));
    }

    private Map<String, Long> snapshotCounts(
            Map<String, AtomicLong> source) {

        Map<String, Long> snapshot = new LinkedHashMap<>();
        source.forEach((key, value) -> snapshot.put(key, value.get()));
        return Map.copyOf(snapshot);
    }
}
