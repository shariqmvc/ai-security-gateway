package com.ai.gateway.entitlement.dto;

import com.ai.gateway.entitlement.enums.Feature;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateTenantEntitlementRequest {

    @NotNull
    private UUID tenantId;

    @NotEmpty
    private Set<Feature> features;

    @NotNull
    @Positive
    private Long requestsPerMinute;

    @NotNull
    @Positive
    private Long requestsPerDay;

    @NotNull
    @Positive
    private Long monthlyTokenQuota;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal monthlyBudget;

}
