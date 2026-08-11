package com.ai.gateway.entitlement.plan;

import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.enums.Plan;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Builder
public class PlanDefaults {

    private final Plan plan;

    private final Set<Feature> features;

    private final long requestsPerMinute;

    private final long requestsPerDay;

    private final long monthlyTokenQuota;

    private final BigDecimal monthlyBudget;
}
