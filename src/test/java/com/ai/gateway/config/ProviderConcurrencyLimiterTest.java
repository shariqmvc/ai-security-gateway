package com.ai.gateway.config;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.observability.PerformanceLogger;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConcurrencyLimiterTest {

    @Mock
    private PerformanceLogger performanceLogger;

    @Test
    void shouldAllowAllRequestsWhenLimiterIsDisabled() {
        ProviderConcurrencyProperties properties = new ProviderConcurrencyProperties();
        ProviderConcurrencyLimiter limiter =
                new ProviderConcurrencyLimiter(
                        properties,
                        performanceLogger
                );

        try (ProviderConcurrencyLimiter.Permit ignored = limiter.acquire(Provider.OLLAMA)) {
            assertNotNull(ignored);
        }
    }

    @Test
    void shouldEnforceConfiguredProviderLimit() throws Exception {
        ProviderConcurrencyProperties properties = new ProviderConcurrencyProperties();
        ProviderConcurrencyProperties.LimitPolicy policy = new ProviderConcurrencyProperties.LimitPolicy();
        policy.setMaxConcurrent(1);
        policy.setAcquireTimeout(Duration.ZERO);
        properties.getProviders().put(Provider.OLLAMA, policy);

        ProviderConcurrencyLimiter limiter =
                new ProviderConcurrencyLimiter(
                        properties,
                        performanceLogger
                );
        ProviderConcurrencyLimiter.Permit first = limiter.acquire(Provider.OLLAMA);
        try {
            assertThrows(
                    ProviderConcurrencyLimitException.class,
                    () -> limiter.acquire(Provider.OLLAMA));
        } finally {
            first.close();
        }
    }

    @Test
    void shouldWaitForPermitWhenConfigured() throws Exception {
        ProviderConcurrencyProperties properties = new ProviderConcurrencyProperties();
        ProviderConcurrencyProperties.LimitPolicy policy = new ProviderConcurrencyProperties.LimitPolicy();
        policy.setMaxConcurrent(1);
        policy.setAcquireTimeout(Duration.ofMillis(500));
        properties.getProviders().put(Provider.OLLAMA, policy);

        ProviderConcurrencyLimiter limiter =
                new ProviderConcurrencyLimiter(
                        properties,
                        performanceLogger
                );
        ProviderConcurrencyLimiter.Permit first = limiter.acquire(Provider.OLLAMA);

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(75);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            first.close();
        });
        releaser.start();

        long started = System.nanoTime();
        try (ProviderConcurrencyLimiter.Permit ignored = limiter.acquire(Provider.OLLAMA)) {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMs >= 50, "second acquisition should have waited");
        }
        releaser.join();
    }
}
