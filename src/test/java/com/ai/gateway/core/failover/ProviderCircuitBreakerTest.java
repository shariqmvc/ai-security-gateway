package com.ai.gateway.core.failover;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCircuitBreakerTest {
    private static final Instant BASE_TIME = Instant.parse("2026-10-01T00:00:00Z");
    private MutableClock clock;
    private ProviderCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(BASE_TIME);
        ProviderCircuitBreakerProperties properties = new ProviderCircuitBreakerProperties();
        properties.setEnabled(true);
        properties.setFailureThreshold(1);
        properties.setDefaultOpenDuration(Duration.ofSeconds(60));
        properties.setTimeoutOpenDuration(Duration.ofSeconds(60));
        breaker = new ProviderCircuitBreaker(properties, clock);
    }

    @Test
    void isolatesCircuitStateByEndpoint() {
        RoutingCandidate endpointA = new RoutingCandidate(Provider.OLLAMA, "llama3.2:3b", "ollama-a");
        RoutingCandidate endpointB = new RoutingCandidate(Provider.OLLAMA, "llama3.2:3b", "ollama-b");

        breaker.recordFailure(endpointA, ProviderFailureCategory.TIMEOUT);

        assertFalse(breaker.allowRequest(endpointA));
        assertTrue(breaker.allowRequest(endpointB));
        assertTrue(breaker.isCurrentlyOpen(endpointA));
        assertFalse(breaker.isCurrentlyOpen(endpointB));
    }

    @Test
    void halfOpenAllowsOnlyOneProbe() {
        RoutingCandidate candidate = new RoutingCandidate(Provider.OLLAMA, "llama3.2:3b", "ollama-a");

        breaker.recordFailure(candidate, ProviderFailureCategory.TIMEOUT);
        assertFalse(breaker.allowRequest(candidate));

        clock.advance(Duration.ofSeconds(60));

        assertTrue(breaker.allowRequest(candidate));
        assertFalse(breaker.allowRequest(candidate));

        breaker.recordSuccess(candidate);

        assertTrue(breaker.allowRequest(candidate));
        assertEquals(0, breaker.consecutiveFailures(candidate));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
        @Override public long millis() { return instant.toEpochMilli(); }
    }
}