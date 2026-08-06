package com.ai.gateway.tenant;

import java.util.Optional;

public interface TenantService {

    Optional<Tenant> findByTenantCode(String tenantCode);

}
