package com.ai.gateway.service;

import com.ai.gateway.entitlement.entity.TenantEntitlement;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.entitlement.repository.TenantEntitlementRepository;
import com.ai.gateway.provisioning.EntitlementProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EntitlementProvisioningConcurrencyTest {

    @Autowired
    private EntitlementProvisioningService provisioningService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantEntitlementRepository entitlementRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID tenantId;

    @AfterEach
    void cleanup() {

        if (tenantId == null) {
            return;
        }

        entitlementRepository
                .findByTenantId(tenantId)
                .ifPresent(entitlementRepository::delete);

        tenantRepository.deleteById(tenantId);
    }

    @Test
    void shouldCreateOnlyOneEntitlementWhenProvisioningConcurrently()
            throws Exception {

        Tenant tenant =
                Tenant.builder()
                        .tenantCode(
                                "CONCURRENT-" +
                                        UUID.randomUUID())
                        .tenantName(
                                "Concurrent Test Tenant")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        tenant =
                tenantRepository.saveAndFlush(tenant);

        tenantId = tenant.getId();

        int threadCount = 2;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        Future<?> first =
                executor.submit(() -> {

                    await(startLatch);

                    provisioningService.provision(
                            tenantId);
                });

        Future<?> second =
                executor.submit(() -> {

                    await(startLatch);

                    provisioningService.provision(
                            tenantId);
                });

        startLatch.countDown();

        first.get();
        second.get();

        executor.shutdown();

        Long entitlementCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM tenant_entitlements
                        WHERE tenant_id = ?
                        """,
                        Long.class,
                        tenantId);

        assertEquals(
                1L,
                entitlementCount);

        TenantEntitlement entitlement =
                entitlementRepository
                        .findByTenantId(tenantId)
                        .orElseThrow();

        assertEquals(
                tenantId,
                entitlement.getTenantId());

        assertTrue(
                entitlement.isEnabled());
    }

    private void await(
            CountDownLatch latch) {

        try {
            latch.await();
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Thread interrupted",
                    e);
        }
    }
}
