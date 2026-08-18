package com.ai.gateway.routing.health;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.failover.ProviderCircuitBreaker;
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
        RoutingCandidate healthy =
                new RoutingCandidate(Provider.OPENAI, "gpt-5");
        RoutingCandidate unhealthy =
                new RoutingCandidate(Provider.GEMINI, "gemini-3.6-flash");

        when(health.isHealthyForRouting(healthy)).thenReturn(true);
        when(health.isHealthyForRouting(unhealthy)).thenReturn(false);

        List<RoutingCandidate> result =
                new FailureAwareCandidateFilter(health)
                        .filter(List.of(healthy, unhealthy));

        assertEquals(List.of(healthy), result);
    }

    @Test
    void removesCandidatesWithOpenCircuitBeforeScoring() {
        RoutingHealthService health = mock(RoutingHealthService.class);
        ProviderCircuitBreaker circuitBreaker =
                mock(ProviderCircuitBreaker.class);

        RoutingCandidate healthy =
                new RoutingCandidate(Provider.OPENAI, "gpt-5");
        RoutingCandidate openCircuit =
                new RoutingCandidate(Provider.GEMINI, "gemini-3.6-flash");

        when(circuitBreaker.isCurrentlyOpen(
                Provider.OPENAI, "gpt-5"))
                .thenReturn(false);

        when(circuitBreaker.isCurrentlyOpen(
                Provider.GEMINI, "gemini-3.6-flash"))
                .thenReturn(true);

        when(health.isHealthyForRouting(healthy))
                .thenReturn(true);

        when(health.isHealthyForRouting(openCircuit))
                .thenReturn(true);

        List<RoutingCandidate> result =
                new FailureAwareCandidateFilter(
                        health,
                        circuitBreaker)
                        .filter(List.of(healthy, openCircuit));

        assertEquals(List.of(healthy), result);

        verify(health, never())
                .isHealthyForRouting(openCircuit);
    }

    @Test
    void circuitHealthCheckDoesNotConsumeHalfOpenProbe() {
        RoutingHealthService health = mock(RoutingHealthService.class);
        ProviderCircuitBreaker circuitBreaker =
                mock(ProviderCircuitBreaker.class);

        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-5");

        when(circuitBreaker.isCurrentlyOpen(
                Provider.OPENAI, "gpt-5"))
                .thenReturn(false);

        when(health.isHealthyForRouting(candidate))
                .thenReturn(true);

        List<RoutingCandidate> result =
                new FailureAwareCandidateFilter(
                        health,
                        circuitBreaker)
                        .filter(List.of(candidate));

        assertEquals(List.of(candidate), result);

        verify(circuitBreaker)
                .isCurrentlyOpen(
                        Provider.OPENAI,
                        "gpt-5");

        verify(circuitBreaker, never())
                .allowRequest(
                        Provider.OPENAI,
                        "gpt-5");
    }
}
