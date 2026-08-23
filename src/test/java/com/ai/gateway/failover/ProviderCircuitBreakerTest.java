package com.ai.gateway.failover;

import com.ai.gateway.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCircuitBreakerTest {

    private ProviderCircuitBreakerProperties properties;
    private ProviderCircuitBreaker breaker;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        properties = new ProviderCircuitBreakerProperties();
        properties.setEnabled(true);
        properties.setFailureThreshold(1);
        properties.setTimeoutOpenDuration(Duration.ofSeconds(60));
        properties.setNetworkOpenDuration(Duration.ofSeconds(30));
        properties.setRateLimitOpenDuration(Duration.ofSeconds(15));
        properties.setServerErrorOpenDuration(Duration.ofSeconds(30));
        properties.setDefaultOpenDuration(Duration.ofSeconds(60));

        clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        breaker = new ProviderCircuitBreaker(properties, clock);
    }

    @Test
    void opensAfterConfiguredFailureThreshold() {
        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertFalse(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        assertTrue(
                breaker.retryAfterMs(
                        Provider.GEMINI,
                        "gemini-test") > 0);
    }

    @Test
    void successClosesCircuit() {
        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertFalse(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        breaker.recordSuccess(
                Provider.GEMINI,
                "gemini-test");

        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));
    }

    @Test
    void failureThresholdCanRequireMultipleFailures() {
        properties.setFailureThreshold(2);

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.NETWORK);

        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.NETWORK);

        assertFalse(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));
    }

    @Test
    void expiredCircuitAllowsHalfOpenProbe() {
        properties.setTimeoutOpenDuration(Duration.ofMillis(5));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertFalse(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        clock.advanceMillis(15);

        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));
    }

    @Test
    void currentOpenCheckDoesNotConsumeHalfOpenProbe() {
        properties.setTimeoutOpenDuration(Duration.ofMillis(5));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertTrue(
                breaker.isCurrentlyOpen(
                        Provider.GEMINI,
                        "gemini-test"));

        clock.advanceMillis(15);

        /*
         * The non-mutating check reports that the cooldown has expired,
         * but does not reserve the half-open probe.
         */
        assertFalse(
                breaker.isCurrentlyOpen(
                        Provider.GEMINI,
                        "gemini-test"));

        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));
    }


    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advanceMillis(long millis) {
            current = current.plusMillis(millis);
        }
    }

}
