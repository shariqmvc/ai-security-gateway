package com.ai.gateway.business.dto;

import com.ai.gateway.business.BusinessType;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.tenant.TenantType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessOnboardingRequest {

    @NotBlank
    private String tenantCode;

    @NotBlank
    private String tenantName;

    @NotNull
    private Plan plan;

    @NotNull
    private TenantType tenantType;

    @NotNull
    private Provider defaultProvider;

    @NotBlank
    private String defaultModel;

    @NotBlank
    private String businessName;

    private String aliasName;
    private String address;
    private String city;
    private String stateProvince;
    private String countryCode;
    private String zipCode;
    private String phone;

    @Email
    private String contactEmail;

    private String websiteUrl;
    private String dba;
    private String companyRegistrationNumber;
    private String taxIdentifier;
    private String dunsNumber;
    private String industry;
    private String employeeCountBand;
    private String timezone;
    private String currencyCode;

    @NotNull
    @Builder.Default
    private BusinessType businessType = BusinessType.STANDARD;

    private String source;
}
