package com.ai.gateway.routing.telemetry;

import com.ai.gateway.core.routing.telemetry.RoutingTelemetryEvent;
import com.ai.gateway.core.routing.telemetry.RoutingTelemetryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutingTelemetryServiceTest {

    @Test
    void shouldAggregateEndpointAwareExecutionTelemetry() {
        RoutingTelemetryService service = new RoutingTelemetryService();

        service.record(event(true, 120, 40L, 10, 20, 30, 0, "stop", null));
        service.record(event(false, 80, null, null, null, null, null, "TIMEOUT"));

        var snapshot = service.snapshot();

        assertEquals(2L, snapshot.totalExecutions());
        assertEquals(1L, snapshot.successfulExecutions());
        assertEquals(1L, snapshot.failedExecutions());
        assertEquals(200L, snapshot.totalLatencyMs());
        assertEquals(100.0, snapshot.averageLatencyMs());

        String key = "OLLAMA/llama3.2:3b@ollama-gpu-02";
        assertEquals(2L, snapshot.executionsByCandidate().get(key));
        assertEquals(1L, snapshot.failuresByCategory().get("TIMEOUT"));

        var stats = snapshot.candidateStats().get(key);
        assertEquals(2L, stats.executions());
        assertEquals(1L, stats.successes());
        assertEquals(1L, stats.failures());
        assertEquals(0.5, stats.successRate());
        assertEquals(100.0, stats.averageLatencyMs());
        assertEquals(100.0, stats.p50LatencyMs());
        assertEquals(118.0, stats.p95LatencyMs());
        assertEquals(40.0, stats.averageTimeToFirstTokenMs());
        assertEquals(40.0, stats.p50TimeToFirstTokenMs());
        assertEquals(40.0, stats.p95TimeToFirstTokenMs());
        assertEquals(10.0, stats.averageInputTokens());
        assertEquals(20.0, stats.averageOutputTokens());
        assertEquals(30.0, stats.averageTotalTokens());
        assertEquals(0.0, stats.averageReasoningTokens());
        assertEquals(166.66666666666666, stats.outputTokensPerSecond());
        assertEquals(1L, stats.finishReasons().get("stop"));
        assertEquals(1L, stats.failureCategories().get("TIMEOUT"));
        assertEquals(2, snapshot.recentEvents().size());
    }

    private RoutingTelemetryEvent event(
            boolean success,
            long latency,
            Long timeToFirstTokenMs,
            Integer input,
            Integer output,
            Integer total,
            Integer reasoning,
            String finishReason,
            String failureCategory) {
        return new RoutingTelemetryEvent(
                UUID.randomUUID(),
                "OLLAMA",
                "llama3.2:3b",
                "ollama-gpu-02",
                success,
                latency,
                timeToFirstTokenMs,
                input,
                output,
                total,
                reasoning,
                finishReason,
                failureCategory,
                Instant.now());
    }
}
