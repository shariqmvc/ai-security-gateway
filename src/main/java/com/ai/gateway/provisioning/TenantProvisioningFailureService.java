package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantProvisioningFailureService {

    private final TenantRepository tenantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID tenantId, String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        LocalDateTime now = LocalDateTime.now();
        tenant.setStatus(TenantStatus.FAILED);
        tenant.setProvisioningStartedAt(now);
        tenant.setProvisioningFailureReason(normalize(reason));
        tenant.setProvisioningCompletedAt(now);
        tenant.setProvisioningAttempts(
                tenant.getProvisioningAttempts() + 1);
        tenantRepository.save(tenant);
    }

    private String normalize(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Tenant provisioning failed.";
        }
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }
}
