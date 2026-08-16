package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.TenantStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TenantProvisioningStatus(
        UUID tenantId,
        TenantStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        int attempts,
        String failureReason) {
}
