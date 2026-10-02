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

        service.record(new RoutingTelemetryEvent(
                UUID.randomUUID(),
                "OLLAMA",
                "llama3.2:3b",
                "ollama-gpu-02",
                true,
                120,
                10,
                20,
                30,
                0,
                "stop",
                null,
                Instant.now()));

        service.record(new RoutingTelemetryEvent(
                UUID.randomUUID(),
                "OLLAMA",
                "llama3.2:3b",
                "ollama-gpu-02",
                false,
                80,
                null,
                null,
                null,
                null,
                null,
                "TIMEOUT",
                Instant.now()));

        var snapshot = service.snapshot();

        assertEquals(2L, snapshot.totalExecutions());
        assertEquals(1L, snapshot.successfulExecutions());
        assertEquals(1L, snapshot.failedExecutions());
        assertEquals(200L, snapshot.totalLatencyMs());
        assertEquals(100.0, snapshot.averageLatencyMs());
        assertEquals(
                2L,
                snapshot.executionsByCandidate()
                        .get("OLLAMA/llama3.2:3b@ollama-gpu-02"));
        assertEquals(1L, snapshot.failuresByCategory().get("TIMEOUT"));
        assertEquals(2, snapshot.recentEvents().size());
    }
}
