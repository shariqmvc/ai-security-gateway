package com.ai.gateway.service;

import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantSchemaContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextConcurrencyTest {

    @AfterEach
    void cleanup() {
        TenantSchemaContext.clear();
        TenantContext.clear();
    }

    @Test
    void shouldKeepTenantContextsIsolatedAcrossConcurrentThreads()
            throws Exception {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            CountDownLatch start = new CountDownLatch(1);

            Future<UUID> a = executor.submit(() -> {
                await(start);
                TenantContext.set(tenantA);
                TenantSchemaContext.set(
                        schemaFor(tenantA));
                try {
                    Thread.sleep(50);
                    assertEquals(tenantA, TenantContext.require());
                    assertEquals(
                            schemaFor(tenantA),
                            TenantSchemaContext.require());
                    return TenantContext.require();
                } finally {
                    TenantSchemaContext.clear();
                    TenantContext.clear();
                }
            });

            Future<UUID> b = executor.submit(() -> {
                await(start);
                TenantContext.set(tenantB);
                TenantSchemaContext.set(
                        schemaFor(tenantB));
                try {
                    Thread.sleep(50);
                    assertEquals(tenantB, TenantContext.require());
                    assertEquals(
                            schemaFor(tenantB),
                            TenantSchemaContext.require());
                    return TenantContext.require();
                } finally {
                    TenantSchemaContext.clear();
                    TenantContext.clear();
                }
            });

            start.countDown();

            assertEquals(tenantA, a.get());
            assertEquals(tenantB, b.get());

            assertNull(TenantContext.get());
            assertNull(TenantSchemaContext.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldNotInheritTenantContextIntoNewThread() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        TenantSchemaContext.set(schemaFor(tenantId));

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {
            Future<Boolean> leaked = executor.submit(
                    () -> TenantContext.get() == null
                            && TenantSchemaContext.get() == null);

            assertTrue(leaked.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static String schemaFor(UUID tenantId) {
        return "tenant_" + tenantId.toString()
                .replace("-", "")
                .toLowerCase();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
