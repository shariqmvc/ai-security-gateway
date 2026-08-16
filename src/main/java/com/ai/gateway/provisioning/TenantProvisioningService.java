package com.ai.gateway.provisioning;

import java.util.UUID;

public interface TenantProvisioningService {

    void provision(UUID tenantId);

    void retry(UUID tenantId);

    TenantProvisioningStatus status(UUID tenantId);
}
