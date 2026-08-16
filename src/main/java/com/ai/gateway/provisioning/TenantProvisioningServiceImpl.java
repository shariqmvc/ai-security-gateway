package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningServiceImpl implements TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningExecutionService executionService;
    private final TenantProvisioningFailureService failureService;

    @Override
    public void provision(UUID tenantId) {
        try {
            executionService.execute(tenantId);
        } catch (RuntimeException ex) {
            try {
                failureService.markFailed(tenantId, ex.getMessage());
            } catch (RuntimeException failureEx) {
                ex.addSuppressed(failureEx);
            }
            throw ex;
        }
    }

    @Override
    public void retry(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return;
        }

        if (tenant.getStatus() != TenantStatus.FAILED) {
            throw new IllegalStateException(
                    "Only FAILED tenants can be retried. Current status: "
                            + tenant.getStatus());
        }

        provision(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantProvisioningStatus status(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        return new TenantProvisioningStatus(
                tenant.getId(),
                tenant.getStatus(),
                tenant.getProvisioningStartedAt(),
                tenant.getProvisioningCompletedAt(),
                tenant.getProvisioningAttempts(),
                tenant.getProvisioningFailureReason());
    }

}
