package com.ai.gateway.tenant;

import com.ai.gateway.tenant.dto.TenantRequest;

import java.util.Optional;

public interface TenantService {

    Optional<Tenant> findByTenantCode(String tenantCode);
    Tenant create(TenantRequest request);

}
