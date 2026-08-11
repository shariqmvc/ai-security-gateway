package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.provisioning.EntitlementProvisioningService;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TenantProvisioningTransactionTest {

    @Autowired
    private TenantService tenantService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EntitlementProvisioningService
            entitlementProvisioningService;

    @AfterEach
    void cleanup() {

        jdbcTemplate.update(
                """
                DELETE FROM tenant_entitlements
                WHERE tenant_id IN (
                    SELECT id
                    FROM tenants
                    WHERE tenant_code = ?
                )
                """,
                "ROLLBACK-TEST");

        jdbcTemplate.update(
                """
                DELETE FROM tenants
                WHERE tenant_code = ?
                """,
                "ROLLBACK-TEST");
    }

    @Test
    void shouldRollbackTenantWhenProvisioningFails() {

        TenantRequest request =
                TenantRequest.builder()
                        .tenantCode("ROLLBACK-TEST")
                        .tenantName("Rollback Test Tenant")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        doThrow(new IllegalStateException(
                "Provisioning failed"))
                .when(entitlementProvisioningService)
                .provision(any(UUID.class));

        assertThrows(
                IllegalStateException.class,
                () -> tenantService.create(request));

        assertTrue(
                tenantRepository
                        .findByTenantCode("ROLLBACK-TEST")
                        .isEmpty());

        Integer entitlementCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM tenant_entitlements te
                        JOIN tenants t
                          ON t.id = te.tenant_id
                        WHERE t.tenant_code = ?
                        """,
                        Integer.class,
                        "ROLLBACK-TEST");

        assertEquals(
                0,
                entitlementCount);
    }
}