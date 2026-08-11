package com.ai.gateway.provisioning;

import java.util.UUID;

public interface EntitlementProvisioningService {
    void provision(UUID tenantId);
}
