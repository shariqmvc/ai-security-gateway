package com.ai.gateway.business.service;

import com.ai.gateway.business.Business;
import com.ai.gateway.business.BusinessStatus;
import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;
import com.ai.gateway.business.repository.BusinessRepository;
import com.ai.gateway.exception.TenantAlreadyExistsException;
import com.ai.gateway.provisioning.TenantProvisioningService;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantService;
import com.ai.gateway.tenant.dto.TenantRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessOnboardingServiceImpl implements BusinessOnboardingService {

    private final BusinessRepository businessRepository;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final TenantProvisioningService tenantProvisioningService;

    @Override
    public BusinessOnboardingResponse onboard(BusinessOnboardingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Business onboarding request is required.");
        }

        Tenant existingTenant = tenantRepository.findByTenantCode(request.getTenantCode()).orElse(null);
        if (existingTenant != null) {
            throw new TenantAlreadyExistsException(request.getTenantCode());
        }

        Business business = Business.builder()
                .name(request.getBusinessName())
                .aliasName(request.getAliasName())
                .address(request.getAddress())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .countryCode(request.getCountryCode())
                .zipCode(request.getZipCode())
                .phone(request.getPhone())
                .contactEmail(request.getContactEmail())
                .websiteUrl(request.getWebsiteUrl())
                .dba(request.getDba())
                .companyRegistrationNumber(request.getCompanyRegistrationNumber())
                .taxIdentifier(request.getTaxIdentifier())
                .dunsNumber(request.getDunsNumber())
                .industry(request.getIndustry())
                .employeeCountBand(request.getEmployeeCountBand())
                .timezone(request.getTimezone())
                .currencyCode(request.getCurrencyCode())
                .businessType(request.getBusinessType())
                .businessStatus(BusinessStatus.PROVISIONING)
                .source(request.getSource())
                .build();

        business = businessRepository.save(business);

        try {
            Tenant tenant = tenantService.create(TenantRequest.builder()
                    .tenantCode(request.getTenantCode())
                    .tenantName(request.getTenantName())
                    .plan(request.getPlan())
                    .type(request.getTenantType())
                    .defaultProvider(request.getDefaultProvider())
                    .defaultModel(request.getDefaultModel())
                    .build());

            business.setTenant(tenant);
            business.setBusinessStatus(BusinessStatus.ACTIVE);
            businessRepository.save(business);
            return toResponse(business);
        } catch (RuntimeException ex) {
            final Business failedBusiness = business;
            tenantRepository.findByTenantCode(request.getTenantCode()).ifPresent(tenant ->
                    failedBusiness.setTenant(tenant));
            business.setBusinessStatus(BusinessStatus.FAILED);
            businessRepository.save(business);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessOnboardingResponse get(UUID businessId) {
        Business business = businessRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business not found: " + businessId));
        return toResponse(business);
    }

    @Override
    public BusinessOnboardingResponse retry(UUID businessId) {
        Business business = businessRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new IllegalStateException("Business not found: " + businessId));

        if (business.getTenant() == null) {
            throw new IllegalStateException("Business has no tenant to retry provisioning: " + businessId);
        }

        if (business.getBusinessStatus() == BusinessStatus.ACTIVE) {
            return toResponse(business);
        }

        business.setBusinessStatus(BusinessStatus.PROVISIONING);
        businessRepository.save(business);

        try {
            tenantProvisioningService.retry(business.getTenant().getId());
            business.setBusinessStatus(BusinessStatus.ACTIVE);
            businessRepository.save(business);
            return toResponse(business);
        } catch (RuntimeException ex) {
            business.setBusinessStatus(BusinessStatus.FAILED);
            businessRepository.save(business);
            throw ex;
        }
    }

    private BusinessOnboardingResponse toResponse(Business business) {
        Tenant tenant = business.getTenant();
        return BusinessOnboardingResponse.builder()
                .businessId(business.getBusinessId())
                .tenantId(tenant == null ? null : tenant.getId())
                .tenantCode(tenant == null ? null : tenant.getTenantCode())
                .businessName(business.getName())
                .businessType(business.getBusinessType())
                .businessStatus(business.getBusinessStatus())
                .tenantStatus(tenant == null ? null : tenant.getStatus())
                .schemaName(tenant == null ? null : tenant.getSchemaName())
                .createdAt(business.getCreatedAt())
                .updatedAt(business.getUpdatedAt())
                .build();
    }
}
