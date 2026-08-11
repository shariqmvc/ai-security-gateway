package com.ai.gateway.entitlement.mapper;

import com.ai.gateway.entitlement.dto.CreateTenantEntitlementRequest;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.entity.TenantEntitlement;

public interface TenantEntitlementMapper {

    TenantEntitlement toEntity(
            CreateTenantEntitlementRequest request);

    TenantEntitlementDto toDto(
            TenantEntitlement entity);

    TenantEntitlementResponse toResponse(
            TenantEntitlement entity);

    TenantEntitlementResponse toResponse(
            TenantEntitlementDto dto);

    void update(
            TenantEntitlement entity,
            UpdateTenantEntitlementRequest request);

}
