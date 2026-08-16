package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.ApiKeyStatus;
import com.ai.gateway.enums.Provider;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TenantProvisioningLifecycleIntegrationTest {

    @Autowired private TenantService tenantService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ApiKeyRepository apiKeyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        tenantRepository.findByTenantCode("PROV-7-3-TEST")
                .ifPresent(tenant -> apiKeyRepository
                        .findByTenantIdAndStatus(tenant.getId(), ApiKeyStatus.ACTIVE)
                        .forEach(key -> apiKeyRepository.deleteById(key.getId())));
        jdbcTemplate.update("DELETE FROM tenant_entitlements WHERE tenant_id IN (SELECT id FROM tenants WHERE tenant_code = ?)", "PROV-7-3-TEST");
        tenantRepository.findByTenantCode("PROV-7-3-TEST")
                .ifPresent(tenant -> tenantRepository.deleteById(tenant.getId()));
    }

    @Test
    void shouldProvisionTenantToActiveAndCreateBootstrapCredential() {
        TenantRequest request = TenantRequest.builder()
                .tenantCode("PROV-7-3-TEST")
                .tenantName("Provisioning Test")
                .plan(Plan.PROFESSIONAL)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        var tenant = tenantService.create(request);

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertEquals(1, tenant.getProvisioningAttempts());
        assertNotNull(tenant.getProvisioningStartedAt());
        assertNotNull(tenant.getProvisioningCompletedAt());
        assertNull(tenant.getProvisioningFailureReason());
        assertEquals(1, apiKeyRepository
                .findByTenantIdAndStatus(tenant.getId(), ApiKeyStatus.ACTIVE)
                .size());
    }
}
