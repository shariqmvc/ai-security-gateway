package com.ai.gateway.service;

import com.ai.gateway.entitlement.entity.TenantEntitlement;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.entitlement.plan.PlanDefaults;
import com.ai.gateway.entitlement.plan.PlanDefaultsProvider;
import com.ai.gateway.entitlement.repository.TenantEntitlementRepository;
import com.ai.gateway.provisioning.EntitlementProvisioningServiceImpl;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementProvisioningServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantEntitlementRepository entitlementRepository;

    @Mock
    private PlanDefaultsProvider planDefaultsProvider;

    @Mock
    private Tenant tenant;

    @InjectMocks
    private EntitlementProvisioningServiceImpl provisioningService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        tenantId = UUID.randomUUID();
    }

    @Test
    void shouldProvisionProfessionalTenantWithDefaultEntitlements() {

        PlanDefaults defaults =
                PlanDefaults.builder()
                        .plan(Plan.PROFESSIONAL)
                        .features(Set.of(
                                Feature.CHAT,
                                Feature.OPENAI,
                                Feature.GEMINI,
                                Feature.CLAUDE,
                                Feature.OLLAMA,
                                Feature.RATE_LIMITING,
                                Feature.QUOTA,
                                Feature.BUDGET
                        ))
                        .requestsPerMinute(100)
                        .requestsPerDay(10_000)
                        .monthlyTokenQuota(10_000_000)
                        .monthlyBudget(
                                new BigDecimal("500.00"))
                        .build();

        when(entitlementRepository.findByTenantId(tenantId))
                .thenReturn(Optional.empty());

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

        when(tenant.getId())
                .thenReturn(tenantId);

        when(tenant.getPlan())
                .thenReturn(Plan.PROFESSIONAL);

        when(planDefaultsProvider.getDefaults(
                Plan.PROFESSIONAL))
                .thenReturn(defaults);

        provisioningService.provision(tenantId);

        ArgumentCaptor<TenantEntitlement> captor =
                ArgumentCaptor.forClass(
                        TenantEntitlement.class);

        verify(entitlementRepository)
                .save(captor.capture());

        TenantEntitlement entitlement =
                captor.getValue();

        assertEquals(
                tenantId,
                entitlement.getTenantId());

        assertEquals(
                defaults.getFeatures(),
                entitlement.getFeatures());

        assertEquals(
                100L,
                entitlement.getRequestsPerMinute());

        assertEquals(
                10_000L,
                entitlement.getRequestsPerDay());

        assertEquals(
                10_000_000L,
                entitlement.getMonthlyTokenQuota());

        assertEquals(
                new BigDecimal("500.00"),
                entitlement.getMonthlyBudget());

        assertTrue(
                entitlement.isEnabled());

        verify(planDefaultsProvider)
                .getDefaults(
                        Plan.PROFESSIONAL);
    }

    @Test
    void shouldNotCreateDuplicateEntitlement() {

        TenantEntitlement existing =
                TenantEntitlement.builder()
                        .tenantId(tenantId)
                        .enabled(true)
                        .build();

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

        when(entitlementRepository.findByTenantId(tenantId))
                .thenReturn(Optional.of(existing));

        provisioningService.provision(tenantId);

        verify(tenantRepository)
                .findByIdForUpdate(tenantId);

        verify(entitlementRepository)
                .findByTenantId(tenantId);

        verify(entitlementRepository, never())
                .save(any(TenantEntitlement.class));

        verify(planDefaultsProvider, never())
                .getDefaults(any(Plan.class));

    }

    @Test
    void shouldThrowWhenTenantDoesNotExist() {

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.empty());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> provisioningService.provision(tenantId));

        assertEquals(
                "Tenant not found: " + tenantId,
                exception.getMessage());

        verify(entitlementRepository, never())
                .findByTenantId(any(UUID.class));

        verify(planDefaultsProvider, never())
                .getDefaults(any(Plan.class));

        verify(entitlementRepository, never())
                .save(any(TenantEntitlement.class));
    }

    @Test
    void shouldThrowWhenTenantHasNoPlan() {

   //     when(entitlementRepository.findByTenantId(tenantId))
     //           .thenReturn(Optional.empty());

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

        when(tenant.getPlan())
                .thenReturn(null);

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                provisioningService.provision(
                                        tenantId));

        assertEquals(
                "Tenant has no plan: " + tenantId,
                exception.getMessage());

        verify(planDefaultsProvider, never())
                .getDefaults(any());

        verify(entitlementRepository, never())
                .save(any(TenantEntitlement.class));
    }

    @Test
    void shouldProvisionStarterUsingProviderDefaults() {

        PlanDefaults defaults =
                PlanDefaults.builder()
                        .plan(Plan.STARTER)
                        .features(Set.of(
                                Feature.CHAT,
                                Feature.OPENAI,
                                Feature.GEMINI,
                                Feature.RATE_LIMITING,
                                Feature.QUOTA
                        ))
                        .requestsPerMinute(30)
                        .requestsPerDay(1_000)
                        .monthlyTokenQuota(1_000_000)
                        .monthlyBudget(
                                new BigDecimal("50.00"))
                        .build();

   //     when(entitlementRepository.findByTenantId(tenantId))
     //           .thenReturn(Optional.empty());

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

        when(tenant.getId())
                .thenReturn(tenantId);

        when(tenant.getPlan())
                .thenReturn(Plan.STARTER);

        when(planDefaultsProvider.getDefaults(
                Plan.STARTER))
                .thenReturn(defaults);

        provisioningService.provision(tenantId);

        ArgumentCaptor<TenantEntitlement> captor =
                ArgumentCaptor.forClass(
                        TenantEntitlement.class);

        verify(entitlementRepository)
                .save(captor.capture());

        TenantEntitlement entitlement =
                captor.getValue();

        assertEquals(
                tenantId,
                entitlement.getTenantId());

        assertEquals(
                defaults.getFeatures(),
                entitlement.getFeatures());

        assertEquals(
                30L,
                entitlement.getRequestsPerMinute());

        assertEquals(
                1_000L,
                entitlement.getRequestsPerDay());

        assertEquals(
                1_000_000L,
                entitlement.getMonthlyTokenQuota());

        assertEquals(
                new BigDecimal("50.00"),
                entitlement.getMonthlyBudget());

        assertTrue(
                entitlement.isEnabled());
    }

    @Test
    void shouldProvisionEnterpriseUsingProviderDefaults() {

        PlanDefaults defaults =
                PlanDefaults.builder()
                        .plan(Plan.ENTERPRISE)
                        .features(Set.of(
                                Feature.CHAT,
                                Feature.OPENAI,
                                Feature.GEMINI,
                                Feature.CLAUDE,
                                Feature.OLLAMA,
                                Feature.RATE_LIMITING,
                                Feature.QUOTA,
                                Feature.BUDGET
                        ))
                        .requestsPerMinute(1_000)
                        .requestsPerDay(100_000)
                        .monthlyTokenQuota(100_000_000)
                        .monthlyBudget(
                                new BigDecimal("5000.00"))
                        .build();

    //    when(entitlementRepository.findByTenantId(tenantId))
        //        .thenReturn(Optional.empty());

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

        when(tenant.getId())
                .thenReturn(tenantId);

        when(tenant.getPlan())
                .thenReturn(Plan.ENTERPRISE);

        when(planDefaultsProvider.getDefaults(
                Plan.ENTERPRISE))
                .thenReturn(defaults);

        provisioningService.provision(tenantId);

        ArgumentCaptor<TenantEntitlement> captor =
                ArgumentCaptor.forClass(
                        TenantEntitlement.class);

        verify(entitlementRepository)
                .save(captor.capture());

        TenantEntitlement entitlement =
                captor.getValue();

        assertEquals(
                tenantId,
                entitlement.getTenantId());

        assertEquals(
                1_000L,
                entitlement.getRequestsPerMinute());

        assertEquals(
                100_000L,
                entitlement.getRequestsPerDay());

        assertEquals(
                100_000_000L,
                entitlement.getMonthlyTokenQuota());

        assertEquals(
                new BigDecimal("5000.00"),
                entitlement.getMonthlyBudget());

        assertTrue(
                entitlement.isEnabled());
    }

    @Test
    void shouldLockTenantBeforeCheckingEntitlement() {

        Tenant tenant =
                Tenant.builder()
                        .id(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .plan(Plan.PROFESSIONAL)
                        .build();

        when(tenantRepository.findByIdForUpdate(tenantId))
                .thenReturn(Optional.of(tenant));

    //    when(entitlementRepository.findByTenantId(tenantId))
         //       .thenReturn(Optional.empty());

        PlanDefaults defaults =
                PlanDefaults.builder()
                        .plan(Plan.PROFESSIONAL)
                        .features(Set.of(
                                Feature.CHAT,
                                Feature.OPENAI,
                                Feature.GEMINI,
                                Feature.CLAUDE,
                                Feature.OLLAMA,
                                Feature.RATE_LIMITING,
                                Feature.QUOTA,
                                Feature.BUDGET
                        ))
                        .requestsPerMinute(100)
                        .requestsPerDay(10_000)
                        .monthlyTokenQuota(10_000_000)
                        .monthlyBudget(new BigDecimal("500.00"))
                        .build();

        when(planDefaultsProvider.getDefaults(
                Plan.PROFESSIONAL))
                .thenReturn(defaults);

        provisioningService.provision(tenantId);

        InOrder inOrder =
                inOrder(
                        tenantRepository,
                        entitlementRepository,
                        planDefaultsProvider);

        inOrder.verify(tenantRepository)
                .findByIdForUpdate(tenantId);

        inOrder.verify(entitlementRepository)
                .findByTenantId(tenantId);

        inOrder.verify(planDefaultsProvider)
                .getDefaults(Plan.PROFESSIONAL);

        inOrder.verify(entitlementRepository)
                .save(any(TenantEntitlement.class));
    }
}
