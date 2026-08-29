package com.ai.gateway.service;

import com.ai.gateway.business.Business;
import com.ai.gateway.business.BusinessStatus;
import com.ai.gateway.business.BusinessType;
import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;
import com.ai.gateway.business.repository.BusinessRepository;
import com.ai.gateway.business.service.BusinessOnboardingServiceImpl;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.provisioning.TenantProvisioningService;
import com.ai.gateway.tenant.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class BusinessOnboardingServiceTest {

    @Mock BusinessRepository businessRepository;
    @Mock TenantRepository tenantRepository;
    @Mock TenantService tenantService;
    @Mock TenantProvisioningService tenantProvisioningService;

    @InjectMocks BusinessOnboardingServiceImpl service;

    @Test
    void onboardingCreatesBusinessAndUsesExistingTenantProvisioning() {
        Tenant tenant = tenant("ACME-001");
        Business saved = business();

        when(tenantRepository.findByTenantCode("ACME-001")).thenReturn(Optional.empty());
        when(businessRepository.save(any(Business.class))).thenReturn(saved);
        when(tenantService.create(any())).thenReturn(tenant);

        BusinessOnboardingResponse response = service.onboard(request());

        assertEquals(saved.getBusinessId(), response.getBusinessId());
        assertEquals(tenant.getId(), response.getTenantId());
        assertEquals(BusinessStatus.ACTIVE, response.getBusinessStatus());
        verify(tenantService).create(any());
    }

    @Test
    void failedTenantProvisioningMarksBusinessFailedAndRetainsTenantLinkWhenAvailable() {
        Tenant tenant = tenant("ACME-002");
        Business saved = business();
        when(tenantRepository.findByTenantCode("ACME-001"))
                .thenReturn(Optional.empty(), Optional.of(tenant));
        when(tenantService.create(any())).thenThrow(new IllegalStateException("provisioning failed"));
        when(businessRepository.save(any(Business.class))).thenReturn(saved);

        assertThrows(IllegalStateException.class, () -> service.onboard(request()));
        verify(businessRepository, atLeast(2)).save(any(Business.class));
    }

    private BusinessOnboardingRequest request() {
        return BusinessOnboardingRequest.builder()
                .tenantCode("ACME-001")
                .tenantName("Acme Tenant")
                .plan(Plan.ENTERPRISE)
                .tenantType(TenantType.ENTERPRISE)
                .defaultProvider(Provider.OLLAMA)
                .defaultModel("llama3.2:3b")
                .businessName("Acme Corporation")
                .businessType(BusinessType.ENTERPRISE)
                .countryCode("IN")
                .build();
    }

    private Business business() {
        return Business.builder()
                .id(1L)
                .businessId(UUID.randomUUID())
                .name("Acme Corporation")
                .businessType(BusinessType.ENTERPRISE)
                .businessStatus(BusinessStatus.PROVISIONING)
                .build();
    }

    private Tenant tenant(String code) {
        return Tenant.builder()
                .id(UUID.randomUUID())
                .tenantCode(code)
                .tenantName("Acme Tenant")
                .schemaName("tenant_" + code.toLowerCase().replace('-', '_'))
                .status(TenantStatus.ACTIVE)
                .type(TenantType.ENTERPRISE)
                .plan(Plan.ENTERPRISE)
                .defaultProvider(Provider.OLLAMA)
                .defaultModel("llama3.2:3b")
                .build();
    }
}
