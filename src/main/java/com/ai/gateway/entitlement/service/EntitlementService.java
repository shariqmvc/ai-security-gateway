package com.ai.gateway.entitlement.service;

import com.ai.gateway.entitlement.dto.CreateTenantEntitlementRequest;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.enums.Feature;

import java.util.UUID;

public interface EntitlementService {
    TenantEntitlementResponse create(
            CreateTenantEntitlementRequest request);

    TenantEntitlementResponse get(
            UUID tenantId);

    TenantEntitlementDto getDto(
            UUID tenantId);

    TenantEntitlementResponse update(
            UUID tenantId,
            UpdateTenantEntitlementRequest request);

    void disable(
            UUID tenantId);

    boolean hasFeature(
            UUID tenantId,
            Feature feature);

    void evict(
            UUID tenantId);

    void clearCache();

    TenantEntitlementResponse provision(UUID tenantId);

    void validateFeature(
            UUID tenantId,
            Feature feature);
}
