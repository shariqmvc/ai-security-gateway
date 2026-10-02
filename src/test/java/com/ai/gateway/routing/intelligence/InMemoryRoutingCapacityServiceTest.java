package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRoutingCapacityServiceTest {

    @Test
    void enforcesEndpointAwareHardLimitAndReleasesSlot() {
        RoutingCapacityProperties properties = new RoutingCapacityProperties();
        properties.setMaxParallelRequests(1);
        InMemoryRoutingCapacityService service =
                new InMemoryRoutingCapacityService(properties);

        RoutingCandidate east =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "east");
        RoutingCandidate west =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "west");

        assertTrue(service.tryAcquire(east));
        assertFalse(service.tryAcquire(east));
        assertTrue(service.tryAcquire(west));
        assertEquals(1, service.inFlight(east));
        assertEquals(1, service.inFlight(west));
        assertTrue(service.isAtHardLimit(east));

        service.release(east);

        assertEquals(0, service.inFlight(east));
        assertTrue(service.tryAcquire(east));
    }

    @Test
    void disabledCapacityDoesNotBlockAdmission() {
        RoutingCapacityProperties properties = new RoutingCapacityProperties();
        properties.setEnabled(false);
        properties.setMaxParallelRequests(1);
        InMemoryRoutingCapacityService service =
                new InMemoryRoutingCapacityService(properties);

        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-test");

        assertTrue(service.tryAcquire(candidate));
        assertTrue(service.tryAcquire(candidate));
        assertFalse(service.isAtHardLimit(candidate));
        assertEquals(0, service.inFlight(candidate));
    }

    @Test
    void endpointSpecificLimitOverridesProviderModelLimit() {
        RoutingCapacityProperties properties = new RoutingCapacityProperties();
        properties.setMaxParallelRequests(3);
        properties.getMaxParallel().put("OPENAI/gpt-test", 2);
        properties.getMaxParallel().put("OPENAI/gpt-test@east", 1);

        InMemoryRoutingCapacityService service =
                new InMemoryRoutingCapacityService(properties);

        RoutingCandidate east =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "east");
        RoutingCandidate west =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "west");

        assertTrue(service.tryAcquire(east));
        assertFalse(service.tryAcquire(east));
        assertTrue(service.tryAcquire(west));
        assertTrue(service.tryAcquire(west));
        assertFalse(service.tryAcquire(west));
    }

    @Test
    void concurrentAdmissionNeverExceedsConfiguredLimit() throws Exception {
        RoutingCapacityProperties properties = new RoutingCapacityProperties();
        properties.setMaxParallelRequests(4);
        InMemoryRoutingCapacityService service =
                new InMemoryRoutingCapacityService(properties);
        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "east");

        int workers = 32;
        var executor = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        AtomicInteger acquired = new AtomicInteger();

        for (int i = 0; i < workers; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (service.tryAcquire(candidate)) {
                        acquired.incrementAndGet();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await();
        executor.shutdownNow();

        assertEquals(4, acquired.get());
        assertEquals(4, service.inFlight(candidate));
    }

    @Test
    void completedUsageAppearsInSnapshot() {
        RoutingCapacityProperties properties = new RoutingCapacityProperties();
        InMemoryRoutingCapacityService service =
                new InMemoryRoutingCapacityService(properties);

        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-test", "default");

        service.recordCompletedRequest(candidate, 250);

        RoutingCapacitySignals signals = service.snapshot();

        assertEquals(1.0, signals.requestsPerMinute().get(candidate.candidateKey()));
        assertEquals(250.0, signals.tokensPerMinute().get(candidate.candidateKey()));
    }
}
