package com.ai.gateway.provisioning;

import com.ai.gateway.entitlement.entity.TenantEntitlement;
import com.ai.gateway.entitlement.plan.PlanDefaults;
import com.ai.gateway.entitlement.plan.PlanDefaultsProvider;
import com.ai.gateway.entitlement.repository.TenantEntitlementRepository;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntitlementProvisioningServiceImpl
        implements EntitlementProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantEntitlementRepository entitlementRepository;
    private final PlanDefaultsProvider planDefaultsProvider;

    @Override
    @Transactional
    public void provision(UUID tenantId) {

        if (entitlementRepository
                .findByTenantId(tenantId)
                .isPresent()) {
            return;
        }

        Tenant tenant =
                tenantRepository.findById(tenantId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Tenant not found: " + tenantId));

        if (tenant.getPlan() == null) {
            throw new IllegalStateException(
                    "Tenant has no plan: " + tenantId);
        }

        PlanDefaults defaults =
                planDefaultsProvider.getDefaults(
                        tenant.getPlan());

        TenantEntitlement entitlement =
                TenantEntitlement.builder()
                        .tenantId(tenant.getId())
                        .features(defaults.getFeatures())
                        .requestsPerMinute(
                                defaults.getRequestsPerMinute())
                        .requestsPerDay(
                                defaults.getRequestsPerDay())
                        .monthlyTokenQuota(
                                defaults.getMonthlyTokenQuota())
                        .monthlyBudget(
                                defaults.getMonthlyBudget())
                        .enabled(true)
                        .build();

        entitlementRepository.save(entitlement);
    }
}
