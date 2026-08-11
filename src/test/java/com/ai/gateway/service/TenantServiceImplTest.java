package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.provisioning.EntitlementProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantServiceImpl;
import com.ai.gateway.tenant.dto.TenantRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {

    @Mock
    private TenantRepository repository;

    @Mock
    private EntitlementProvisioningService
            entitlementProvisioningService;

    @InjectMocks
    private TenantServiceImpl tenantService;

    @Test
    void shouldCreateTenantAndProvisionEntitlement() {

        TenantRequest request =
                TenantRequest.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        Tenant savedTenant =
                Tenant.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        when(repository.findByTenantCode("ACME"))
                .thenReturn(Optional.empty());

        when(repository.save(any(Tenant.class)))
                .thenReturn(savedTenant);

        Tenant result =
                tenantService.create(request);

        assertNotNull(result);

        assertEquals(
                "ACME",
                result.getTenantCode());

        assertEquals(
                "ACME Corporation",
                result.getTenantName());

        assertEquals(
                Plan.PROFESSIONAL,
                result.getPlan());

        verify(repository)
                .save(any(Tenant.class));

        verify(entitlementProvisioningService)
                .provision(savedTenant.getId());
    }

    @Test
    void shouldRejectDuplicateTenantCode() {

        Tenant existing =
                Tenant.builder()
                        .tenantCode("ACME")
                        .tenantName("Existing")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        TenantRequest request =
                TenantRequest.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        when(repository.findByTenantCode("ACME"))
                .thenReturn(Optional.of(existing));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> tenantService.create(request));

        assertEquals(
                "Tenant already exists: ACME",
                exception.getMessage());

        verify(repository, never())
                .save(any(Tenant.class));

        verifyNoInteractions(
                entitlementProvisioningService);
    }

    @Test
    void shouldCopyRequestValuesIntoTenant() {

        TenantRequest request =
                TenantRequest.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .plan(Plan.STARTER)
                        .build();

        when(repository.findByTenantCode("TEST"))
                .thenReturn(Optional.empty());

        when(repository.save(any(Tenant.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0));

        tenantService.create(request);

        ArgumentCaptor<Tenant> captor =
                ArgumentCaptor.forClass(
                        Tenant.class);

        verify(repository)
                .save(captor.capture());

        Tenant tenant =
                captor.getValue();

        assertEquals(
                "TEST",
                tenant.getTenantCode());

        assertEquals(
                "Test Tenant",
                tenant.getTenantName());

        assertEquals(
                Plan.STARTER,
                tenant.getPlan());

        assertNotNull(
                tenant.getCreatedAt());
    }

    @Test
    void shouldPropagateProvisioningFailure() {

        TenantRequest request =
                TenantRequest.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        Tenant savedTenant =
                Tenant.builder()
                        .tenantCode("ACME")
                        .tenantName("ACME Corporation")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        when(repository.findByTenantCode("ACME"))
                .thenReturn(Optional.empty());

        when(repository.save(any(Tenant.class)))
                .thenReturn(savedTenant);

        doThrow(new IllegalStateException(
                "Provisioning failed"))
                .when(entitlementProvisioningService)
                .provision(savedTenant.getId());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> tenantService.create(request));

        assertEquals(
                "Provisioning failed",
                exception.getMessage());

        verify(repository)
                .save(any(Tenant.class));

        verify(entitlementProvisioningService)
                .provision(savedTenant.getId());
    }
}
