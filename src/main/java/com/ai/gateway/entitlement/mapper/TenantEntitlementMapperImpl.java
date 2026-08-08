package com.ai.gateway.entitlement.mapper;

import com.ai.gateway.entitlement.dto.CreateTenantEntitlementRequest;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.entity.TenantEntitlement;
import org.springframework.stereotype.Component;

@Component
public class TenantEntitlementMapperImpl
        implements TenantEntitlementMapper {

    @Override
    public TenantEntitlement toEntity(
            CreateTenantEntitlementRequest request) {

        return TenantEntitlement.builder()
                .tenantId(request.getTenantId())
                .features(request.getFeatures())
                .requestsPerMinute(
                        request.getRequestsPerMinute())
                .requestsPerDay(
                        request.getRequestsPerDay())
                .monthlyTokenQuota(
                        request.getMonthlyTokenQuota())
                .monthlyBudget(
                        request.getMonthlyBudget())
                .enabled(true)
                .build();
    }

    @Override
    public TenantEntitlementDto toDto(
            TenantEntitlement entity) {

        return TenantEntitlementDto.builder()
                .tenantId(entity.getTenantId())
                .features(entity.getFeatures())
                .requestsPerMinute(
                        entity.getRequestsPerMinute())
                .requestsPerDay(
                        entity.getRequestsPerDay())
                .monthlyTokenQuota(
                        entity.getMonthlyTokenQuota())
                .monthlyBudget(
                        entity.getMonthlyBudget())
                .enabled(entity.isEnabled())
                .build();
    }

    @Override
    public TenantEntitlementResponse toResponse(
            TenantEntitlement entity) {

        return TenantEntitlementResponse.builder()
                .tenantId(entity.getTenantId())
                .features(entity.getFeatures())
                .requestsPerMinute(
                        entity.getRequestsPerMinute())
                .requestsPerDay(
                        entity.getRequestsPerDay())
                .monthlyTokenQuota(
                        entity.getMonthlyTokenQuota())
                .monthlyBudget(
                        entity.getMonthlyBudget())
                .enabled(entity.isEnabled())
                .build();
    }

    @Override
    public TenantEntitlementResponse toResponse(
            TenantEntitlementDto dto) {

        return TenantEntitlementResponse.builder()
                .tenantId(dto.getTenantId())
                .features(dto.getFeatures())
                .requestsPerMinute(
                        dto.getRequestsPerMinute())
                .requestsPerDay(
                        dto.getRequestsPerDay())
                .monthlyTokenQuota(
                        dto.getMonthlyTokenQuota())
                .monthlyBudget(
                        dto.getMonthlyBudget())
                .enabled(dto.getEnabled())
                .build();
    }

    @Override
    public void update(
            TenantEntitlement entity,
            UpdateTenantEntitlementRequest request) {

        if (request.getFeatures() != null) {
            entity.setFeatures(
                    request.getFeatures());
        }

        if (request.getRequestsPerMinute() != null) {
            entity.setRequestsPerMinute(
                    request.getRequestsPerMinute());
        }

        if (request.getRequestsPerDay() != null) {
            entity.setRequestsPerDay(
                    request.getRequestsPerDay());
        }

        if (request.getMonthlyTokenQuota() != null) {
            entity.setMonthlyTokenQuota(
                    request.getMonthlyTokenQuota());
        }

        if (request.getMonthlyBudget() != null) {
            entity.setMonthlyBudget(
                    request.getMonthlyBudget());
        }

        if (request.getEnabled() != null) {
            entity.setEnabled(
                    request.getEnabled());
        }
    }
}