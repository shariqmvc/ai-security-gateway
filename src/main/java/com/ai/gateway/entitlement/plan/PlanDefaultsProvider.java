package com.ai.gateway.entitlement.plan;

import com.ai.gateway.entitlement.enums.Plan;

public interface PlanDefaultsProvider {
    PlanDefaults getDefaults(Plan plan);
}
