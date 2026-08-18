package com.ai.gateway.failover;

import com.ai.gateway.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCircuitBreakerTest {

    private ProviderCircuitBreakerProperties properties;
    private ProviderCircuitBreaker breaker;

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

        breaker = new ProviderCircuitBreaker(properties);
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
    void expiredCircuitAllowsHalfOpenProbe() throws Exception {
        properties.setTimeoutOpenDuration(Duration.ofMillis(5));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertFalse(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));

        Thread.sleep(15);

        assertTrue(
                breaker.allowRequest(
                        Provider.GEMINI,
                        "gemini-test"));
    }
    @Test
    void currentOpenCheckDoesNotConsumeHalfOpenProbe() throws Exception {
        properties.setTimeoutOpenDuration(Duration.ofMillis(5));

        breaker.recordFailure(
                Provider.GEMINI,
                "gemini-test",
                ProviderFailureCategory.TIMEOUT);

        assertTrue(
                breaker.isCurrentlyOpen(
                        Provider.GEMINI,
                        "gemini-test"));

        Thread.sleep(15);

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

}
