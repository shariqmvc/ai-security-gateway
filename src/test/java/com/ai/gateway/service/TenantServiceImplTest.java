package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.provisioning.TenantProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantServiceImpl;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private TenantRepository repository;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Test
    void shouldCreateTenantAndProvision() {
        TenantRequest request = request("ACME", Plan.PROFESSIONAL);
        Tenant savedTenant = Tenant.builder()
                .id(java.util.UUID.randomUUID())
                .tenantCode("ACME")
                .tenantName("ACME Corporation")
                .status(TenantStatus.REQUESTED)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        when(repository.findByTenantCode("ACME")).thenReturn(Optional.empty());
        when(repository.save(any(Tenant.class))).thenReturn(savedTenant);
        when(repository.findById(savedTenant.getId())).thenReturn(Optional.of(
                Tenant.builder().id(savedTenant.getId())
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .status(TenantStatus.ACTIVE)
                        .type(TenantType.STANDARD)
                        .plan(Plan.PROFESSIONAL)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-3.6-flash")
                        .build()));

        Tenant result = tenantService.create(request);

        assertEquals("ACME", result.getTenantCode());
        assertEquals(TenantStatus.ACTIVE, result.getStatus());
        verify(tenantProvisioningService).provision(savedTenant.getId());
    }

    @Test
    void shouldStartNewTenantAsRequested() {
        TenantRequest request = request("TEST", Plan.STARTER);

        Tenant savedTenant = Tenant.builder()
                .id(UUID.randomUUID())
                .tenantCode("TEST")
                .tenantName("Test Tenant")
                .status(TenantStatus.REQUESTED)
                .type(TenantType.STANDARD)
                .plan(Plan.STARTER)
                .defaultProvider(Provider.OLLAMA)
                .defaultModel("llama3.1:8b")
                .build();

        when(repository.findByTenantCode("TEST"))
                .thenReturn(Optional.empty());

        when(repository.save(any(Tenant.class)))
                .thenReturn(savedTenant);

        when(repository.findById(savedTenant.getId()))
                .thenReturn(Optional.of(savedTenant));

        tenantService.create(request);

        ArgumentCaptor<Tenant> captor =
                ArgumentCaptor.forClass(Tenant.class);

        verify(repository).save(captor.capture());

        assertEquals(
                TenantStatus.REQUESTED,
                captor.getValue().getStatus());
    }

    @Test
    void shouldRejectDuplicateTenantCode() {
        Tenant existing = Tenant.builder()
                .tenantCode("ACME")
                .tenantName("Existing")
                .plan(Plan.PROFESSIONAL)
                .build();
        when(repository.findByTenantCode("ACME")).thenReturn(Optional.of(existing));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, () -> tenantService.create(request("ACME", Plan.PROFESSIONAL)));

        assertEquals("Tenant already exists: ACME", exception.getMessage());
        verify(repository, never()).save(any(Tenant.class));
        verifyNoInteractions(tenantProvisioningService);
    }

    @Test
    void shouldRejectNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> tenantService.create(null));
        verifyNoInteractions(repository, tenantProvisioningService);
    }

    @Test
    void shouldRejectMissingTenantType() {
        TenantRequest request = TenantRequest.builder()
                .tenantCode("ACME")
                .tenantName("ACME Corporation")
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        assertThrows(IllegalArgumentException.class, () -> tenantService.create(request));
        verifyNoInteractions(repository, tenantProvisioningService);
    }

    @Test
    void shouldPropagateProvisioningFailure() {
        TenantRequest request = request("ACME", Plan.PROFESSIONAL);
        Tenant savedTenant = Tenant.builder()
                .id(java.util.UUID.randomUUID())
                .tenantCode("ACME")
                .tenantName("ACME Corporation")
                .status(TenantStatus.REQUESTED)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();
        when(repository.findByTenantCode("ACME")).thenReturn(Optional.empty());
        when(repository.save(any(Tenant.class))).thenReturn(savedTenant);
        doThrow(new IllegalStateException("Provisioning failed"))
                .when(tenantProvisioningService).provision(savedTenant.getId());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> tenantService.create(request));
        assertEquals("Provisioning failed", exception.getMessage());
        verify(tenantProvisioningService).provision(savedTenant.getId());
    }

    private TenantRequest request(String code, Plan plan) {
        return TenantRequest.builder()
                .tenantCode(code)
                .tenantName("Test Tenant")
                .plan(plan)
                .type(TenantType.STANDARD)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();
    }
}
