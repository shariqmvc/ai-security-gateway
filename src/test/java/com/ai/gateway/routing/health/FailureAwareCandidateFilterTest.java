package com.ai.gateway.routing.health;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ActiveProfiles("test")
class FailureAwareCandidateFilterTest {

    @Test
    void removesUnhealthyCandidates() {
        RoutingHealthService health = mock(RoutingHealthService.class);
        RoutingCandidate healthy = new RoutingCandidate(Provider.OPENAI, "gpt-5");
        RoutingCandidate unhealthy = new RoutingCandidate(Provider.GEMINI, "gemini-3.6-flash");

        when(health.isHealthyForRouting(healthy)).thenReturn(true);
        when(health.isHealthyForRouting(unhealthy)).thenReturn(false);

        List<RoutingCandidate> result =
                new FailureAwareCandidateFilter(health)
                        .filter(List.of(healthy, unhealthy));

        assertEquals(List.of(healthy), result);
    }
}
