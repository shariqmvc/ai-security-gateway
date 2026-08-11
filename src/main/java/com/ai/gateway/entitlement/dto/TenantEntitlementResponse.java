package com.ai.gateway.entitlement.dto;

import com.ai.gateway.entitlement.enums.Feature;
import lombok.*;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntitlementResponse {

    private UUID tenantId;

    private Set<Feature> features;

    private Long requestsPerMinute;

    private Long requestsPerDay;

    private Long monthlyTokenQuota;

    private BigDecimal monthlyBudget;

    private Boolean enabled;

}
