package com.ai.gateway.core.routing.telemetry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RoutingTelemetryService {

    private static final int MAX_RECENT_EVENTS = 200;

    private final AtomicLong totalExecutions = new AtomicLong();
    private final AtomicLong successfulExecutions = new AtomicLong();
    private final AtomicLong failedExecutions = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();

    private final Map<String, AtomicLong> executionsByCandidate = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failuresByCategory = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<RoutingTelemetryEvent> recentEvents =
            new ConcurrentLinkedDeque<>();

    public void record(RoutingTelemetryEvent event) {
        if (event == null || event.provider() == null || event.model() == null) return;

        totalExecutions.incrementAndGet();
        totalLatencyMs.addAndGet(event.latencyMs());

        if (event.success()) {
            successfulExecutions.incrementAndGet();
        } else {
            failedExecutions.incrementAndGet();
            failuresByCategory.computeIfAbsent(
                    normalize(event.failureCategory(), "UNKNOWN"), ignored -> new AtomicLong())
                    .incrementAndGet();
        }

        executionsByCandidate.computeIfAbsent(event.candidateKey(), ignored -> new AtomicLong())
                .incrementAndGet();

        recentEvents.addFirst(event);
        while (recentEvents.size() > MAX_RECENT_EVENTS) recentEvents.pollLast();
    }

    public RoutingTelemetrySnapshot snapshot() {
        long total = totalExecutions.get();
        long latency = totalLatencyMs.get();
        List<RoutingTelemetryEvent> events = new ArrayList<>(recentEvents);

        return new RoutingTelemetrySnapshot(
                total,
                successfulExecutions.get(),
                failedExecutions.get(),
                latency,
                total == 0 ? 0.0 : (double) latency / total,
                snapshotCounts(executionsByCandidate),
                snapshotCounts(failuresByCategory),
                buildCandidateStats(events),
                events);
    }

    private Map<String, RoutingTelemetrySnapshot.CandidateTelemetryStats> buildCandidateStats(
            List<RoutingTelemetryEvent> events) {
        Map<String, List<RoutingTelemetryEvent>> grouped = events.stream()
                .collect(Collectors.groupingBy(
                        RoutingTelemetryEvent::candidateKey,
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, RoutingTelemetrySnapshot.CandidateTelemetryStats> result = new LinkedHashMap<>();
        grouped.forEach((candidate, samples) -> result.put(candidate, stats(samples)));
        return Map.copyOf(result);
    }

    private RoutingTelemetrySnapshot.CandidateTelemetryStats stats(
            List<RoutingTelemetryEvent> samples) {
        long executions = samples.size();
        long successes = samples.stream().filter(RoutingTelemetryEvent::success).count();
        long failures = executions - successes;

        double avgLatency = average(samples.stream().mapToLong(RoutingTelemetryEvent::latencyMs).boxed().toList());
        double p50 = percentile(samples, 0.50);
        double p95 = percentile(samples, 0.95);
        double avgTimeToFirstToken = averageNullableLong(samples, Metric.TTFT);
        double p50TimeToFirstToken = percentileNullable(samples, Metric.TTFT, 0.50);
        double p95TimeToFirstToken = percentileNullable(samples, Metric.TTFT, 0.95);

        double avgInput = averageNullable(samples, Metric.INPUT);
        double avgOutput = averageNullable(samples, Metric.OUTPUT);
        double avgTotal = averageNullable(samples, Metric.TOTAL);
        double avgReasoning = averageNullable(samples, Metric.REASONING);

        double outputTokensPerSecond = samples.stream()
                .filter(e -> e.outputTokens() != null && e.outputTokens() >= 0 && e.latencyMs() > 0)
                .mapToDouble(e -> e.outputTokens() * 1000.0 / e.latencyMs())
                .average()
                .orElse(0.0);

        Map<String, Long> finishes = samples.stream()
                .map(RoutingTelemetryEvent::finishReason)
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> failuresByCategory = samples.stream()
                .filter(e -> !e.success())
                .map(e -> normalize(e.failureCategory(), "UNKNOWN"))
                .collect(Collectors.groupingBy(v -> v, LinkedHashMap::new, Collectors.counting()));

        return new RoutingTelemetrySnapshot.CandidateTelemetryStats(
                executions,
                successes,
                failures,
                executions == 0 ? 0.0 : (double) successes / executions,
                avgLatency,
                p50,
                p95,
                avgTimeToFirstToken,
                p50TimeToFirstToken,
                p95TimeToFirstToken,
                avgInput,
                avgOutput,
                avgTotal,
                avgReasoning,
                outputTokensPerSecond,
                finishes,
                failuresByCategory);
    }

    private double percentile(List<RoutingTelemetryEvent> samples, double percentile) {
        long[] values = samples.stream().mapToLong(RoutingTelemetryEvent::latencyMs).sorted().toArray();
        if (values.length == 0) return 0.0;
        if (values.length == 1) return values[0];

        double rank = percentile * (values.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return values[lower];
        double weight = rank - lower;
        return values[lower] + (values[upper] - values[lower]) * weight;
    }

    private double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double averageNullable(List<RoutingTelemetryEvent> samples, Metric metric) {
        return samples.stream()
                .map(metric::value)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    private double averageNullableLong(List<RoutingTelemetryEvent> samples, Metric metric) {
        return samples.stream()
                .map(metric::longValue)
                .filter(v -> v != null)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    private double percentileNullable(
            List<RoutingTelemetryEvent> samples,
            Metric metric,
            double percentile) {
        long[] values = samples.stream()
                .map(metric::longValue)
                .filter(v -> v != null)
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();
        if (values.length == 0) return 0.0;
        if (values.length == 1) return values[0];

        double rank = percentile * (values.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) return values[lower];
        double weight = rank - lower;
        return values[lower] + (values[upper] - values[lower]) * weight;
    }

    private Map<String, Long> snapshotCounts(Map<String, AtomicLong> source) {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        source.forEach((key, value) -> snapshot.put(key, value.get()));
        return Map.copyOf(snapshot);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private enum Metric {
        INPUT {
            @Override Integer value(RoutingTelemetryEvent e) { return e.inputTokens(); }
        },
        OUTPUT {
            @Override Integer value(RoutingTelemetryEvent e) { return e.outputTokens(); }
        },
        TOTAL {
            @Override Integer value(RoutingTelemetryEvent e) { return e.totalTokens(); }
        },
        REASONING {
            @Override Integer value(RoutingTelemetryEvent e) { return e.reasoningTokens(); }
        },
        TTFT {
            @Override Integer value(RoutingTelemetryEvent e) {
                Long value = e.timeToFirstTokenMs();
                return value == null ? null : (int) Math.min(Integer.MAX_VALUE, value);
            }

            @Override Long longValue(RoutingTelemetryEvent e) {
                return e.timeToFirstTokenMs();
            }
        };

        abstract Integer value(RoutingTelemetryEvent event);

        Long longValue(RoutingTelemetryEvent event) {
            Integer value = value(event);
            return value == null ? null : value.longValue();
        }
    }
}
