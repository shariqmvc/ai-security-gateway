package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.provisioning.EntitlementProvisioningService;
import com.ai.gateway.security.ApiKeyRepository;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"gemini.api.key=test-gemini-key"})
class TenantProvisioningTransactionTest {

    @Autowired private TenantService tenantService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ApiKeyRepository apiKeyRepository;

    @MockitoBean
    private EntitlementProvisioningService entitlementProvisioningService;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM API_KEYS WHERE tenant_id IN (SELECT id FROM tenants WHERE tenant_code = ?)", "ROLLBACK-TEST");
        jdbcTemplate.update("DELETE FROM tenant_entitlements WHERE tenant_id IN (SELECT id FROM tenants WHERE tenant_code = ?)", "ROLLBACK-TEST");
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_code = ?", "ROLLBACK-TEST");
    }

    @Test
    void shouldPersistFailedTenantWhenProvisioningFails() {
        TenantRequest request = TenantRequest.builder()
                .tenantCode("ROLLBACK-TEST")
                .tenantName("Rollback Test Tenant")
                .plan(Plan.PROFESSIONAL)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        doThrow(new IllegalStateException("Provisioning failed"))
                .when(entitlementProvisioningService)
                .provision(any());

        assertThrows(IllegalStateException.class, () -> tenantService.create(request));

        var tenant = tenantRepository.findByTenantCode("ROLLBACK-TEST").orElseThrow();
        assertEquals(TenantStatus.FAILED, tenant.getStatus());
        assertEquals("Provisioning failed", tenant.getProvisioningFailureReason());
        assertEquals(1, tenant.getProvisioningAttempts());
        assertTrue(apiKeyRepository.findByTenantIdAndStatus(tenant.getId(), com.ai.gateway.enums.ApiKeyStatus.ACTIVE).isEmpty());
    }
}
