package com.ai.gateway.provisioning;

import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningExecutionService {

    private final TenantRepository tenantRepository;
    private final EntitlementProvisioningService entitlementProvisioningService;
    private final ApiKeyService apiKeyService;

    @Transactional
    public void execute(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return;
        }

        if (tenant.getStatus() != TenantStatus.REQUESTED
                && tenant.getStatus() != TenantStatus.FAILED
                && tenant.getStatus() != TenantStatus.PROVISIONING) {
            throw new IllegalStateException(
                    "Tenant cannot be provisioned from status: "
                            + tenant.getStatus());
        }

        tenant.setStatus(TenantStatus.PROVISIONING);
        tenant.setProvisioningStartedAt(LocalDateTime.now());
        tenant.setProvisioningCompletedAt(null);
        tenant.setProvisioningFailureReason(null);
        tenant.setProvisioningAttempts(
                tenant.getProvisioningAttempts() + 1);
        tenantRepository.save(tenant);

        entitlementProvisioningService.provision(tenantId);
        apiKeyService.provisionInitialKey(tenant);

        tenant.setStatus(TenantStatus.VALIDATING);
        tenantRepository.save(tenant);

        validate(tenant);

        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setProvisioningCompletedAt(LocalDateTime.now());
        tenant.setProvisioningFailureReason(null);
        tenantRepository.save(tenant);
    }

    private void validate(Tenant tenant) {
        if (tenant.getPlan() == null) {
            throw new IllegalStateException(
                    "Tenant validation failed: plan is missing");
        }
        if (tenant.getType() == null) {
            throw new IllegalStateException(
                    "Tenant validation failed: type is missing");
        }
        if (tenant.getDefaultProvider() == null) {
            throw new IllegalStateException(
                    "Tenant validation failed: default provider is missing");
        }
        if (tenant.getDefaultModel() == null
                || tenant.getDefaultModel().isBlank()) {
            throw new IllegalStateException(
                    "Tenant validation failed: default model is missing");
        }
    }
}
