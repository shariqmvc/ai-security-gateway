package com.ai.gateway.config;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fair provider-level concurrency limiter.
 *
 * <p>Provider-wide rather than tenant-specific. All tenants compete fairly
 * for the same downstream provider capacity.</p>
 */
@Component
public class ProviderConcurrencyLimiter {

    private final Map<Provider, Semaphore> semaphores =
            new EnumMap<>(Provider.class);

    private final Map<Provider, AtomicInteger> activeCounts =
            new EnumMap<>(Provider.class);

    private final ProviderConcurrencyProperties properties;
    private final PerformanceLogger performanceLogger;

    public ProviderConcurrencyLimiter(
            ProviderConcurrencyProperties properties,
            PerformanceLogger performanceLogger) {

        this.properties = properties;
        this.performanceLogger = performanceLogger;
    }

    public Permit acquire(
            UUID requestId,
            Provider provider) {

        ProviderConcurrencyProperties.LimitPolicy policy =
                properties.forProvider(provider);

        int maxConcurrent =
                policy.getMaxConcurrent();

        /*
         * maxConcurrent <= 0 means the limiter is disabled.
         */
        if (maxConcurrent <= 0) {
            return Permit.NOOP;
        }

        Semaphore semaphore =
                semaphoreFor(provider, maxConcurrent);

        AtomicInteger activeCount =
                activeCountFor(provider);

        Duration configuredWait =
                policy.getAcquireTimeout();

        long waitMillis =
                configuredWait == null
                        ? 0L
                        : Math.max(
                        0L,
                        configuredWait.toMillis()
                );

        /*
         * Respect the remaining provider request budget.
         */
        long remainingBudget =
                ProviderRequestBudget.remainingMillis();

        if (ProviderRequestBudget.isActive()) {
            waitMillis =
                    Math.min(
                            waitMillis,
                            remainingBudget
                    );
        }

        long acquireStarted =
                System.nanoTime();

        boolean acquired;

        /*
         * First attempt immediate acquisition.
         *
         * This is important because we only emit WAIT if the request
         * actually failed to acquire immediately.
         */
        try {

            acquired =
                    semaphore.tryAcquire();

            if (!acquired && waitMillis > 0L) {

                performanceLogger.providerConcurrencyWait(
                        requestId,
                        provider.name(),
                        maxConcurrent,
                        waitMillis,
                        activeCount.get()
                );

                acquired =
                        semaphore.tryAcquire(
                                waitMillis,
                                TimeUnit.MILLISECONDS
                        );
            }

        } catch (InterruptedException ex) {

            Thread.currentThread().interrupt();

            throw new ProviderConcurrencyLimitException(
                    "Interrupted while waiting for "
                            + provider
                            + " concurrency capacity."
            );
        }

        long acquireWaitMs =
                (
                        System.nanoTime()
                                - acquireStarted
                ) / 1_000_000L;

        if (!acquired) {

            performanceLogger.providerConcurrencyRejected(
                    requestId,
                    provider.name(),
                    maxConcurrent,
                    acquireWaitMs,
                    activeCount.get()
            );

            throw new ProviderConcurrencyLimitException(
                    "Provider concurrency capacity exhausted for "
                            + provider
                            + " (maxConcurrent="
                            + maxConcurrent
                            + ", acquireTimeout="
                            + acquireWaitMs
                            + "ms)."
            );
        }

        /*
         * The request has now crossed the actual downstream
         * concurrency boundary.
         */
        int active =
                activeCount.incrementAndGet();

        performanceLogger.providerConcurrencyAcquired(
                requestId,
                provider.name(),
                maxConcurrent,
                active,
                acquireWaitMs
        );

        return new Permit(
                requestId,
                provider,
                semaphore,
                activeCount,
                maxConcurrent,
                performanceLogger
        );
    }

    /*
     * Backwards-compatible overload for existing unit tests/callers.
     */
    public Permit acquire(Provider provider) {
        return acquire(null, provider);
    }

    private synchronized Semaphore semaphoreFor(
            Provider provider,
            int maxConcurrent) {

        Semaphore existing =
                semaphores.get(provider);

        if (existing != null) {
            return existing;
        }

        Semaphore created =
                new Semaphore(
                        maxConcurrent,
                        true
                );

        semaphores.put(
                provider,
                created
        );

        return created;
    }

    private synchronized AtomicInteger activeCountFor(
            Provider provider) {

        AtomicInteger existing =
                activeCounts.get(provider);

        if (existing != null) {
            return existing;
        }

        AtomicInteger created =
                new AtomicInteger(0);

        activeCounts.put(
                provider,
                created
        );

        return created;
    }

    public static final class Permit
            implements AutoCloseable {

        private static final Permit NOOP =
                new Permit();

        private final UUID requestId;
        private final Provider provider;
        private final Semaphore semaphore;
        private final AtomicInteger activeCount;
        private final int maxConcurrent;
        private final PerformanceLogger performanceLogger;

        private boolean released;

        private Permit() {
            this.requestId = null;
            this.provider = null;
            this.semaphore = null;
            this.activeCount = null;
            this.maxConcurrent = 0;
            this.performanceLogger = null;
            this.released = true;
        }

        private Permit(
                UUID requestId,
                Provider provider,
                Semaphore semaphore,
                AtomicInteger activeCount,
                int maxConcurrent,
                PerformanceLogger performanceLogger) {

            this.requestId = requestId;
            this.provider = provider;
            this.semaphore = semaphore;
            this.activeCount = activeCount;
            this.maxConcurrent = maxConcurrent;
            this.performanceLogger = performanceLogger;
            this.released = false;
        }

        @Override
        public void close() {

            if (released) {
                return;
            }

            released = true;

            int active =
                    activeCount.decrementAndGet();

            semaphore.release();

            performanceLogger.providerConcurrencyReleased(
                    requestId,
                    provider.name(),
                    maxConcurrent,
                    active
            );
        }
    }
}