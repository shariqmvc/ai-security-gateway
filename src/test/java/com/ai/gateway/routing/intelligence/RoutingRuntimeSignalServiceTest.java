package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.config.RoutingScoringProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
@ActiveProfiles("test")
class RoutingRuntimeSignalServiceTest {
    @Test
    void runtimeSuccessUpdatesLatencySignal() {
        RoutingScoringProperties properties = new RoutingScoringProperties();
        properties.getDefaults().setLatencyMs(1000.0);
        RoutingRuntimeSignalService service = new RoutingRuntimeSignalService(properties);
        RoutingCandidate candidate = new RoutingCandidate(Provider.OPENAI, "gpt-a");

        service.recordSuccess(candidate, 100);

        assertTrue(service.currentLatency(candidate) < 1000.0);
        assertTrue(service.currentAvailability(candidate) > 0.0);
    }

    @Test
    void failuresReduceAvailabilitySignal() {
        RoutingScoringProperties properties = new RoutingScoringProperties();
        RoutingRuntimeSignalService service = new RoutingRuntimeSignalService(properties);
        RoutingCandidate candidate = new RoutingCandidate(Provider.OPENAI, "gpt-a");

        service.recordFailure(candidate);
        service.recordFailure(candidate);
        service.recordFailure(candidate);

        assertTrue(service.currentAvailability(candidate) < 1.0);
    }
}
