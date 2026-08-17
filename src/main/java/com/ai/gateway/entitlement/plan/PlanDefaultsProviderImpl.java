package com.ai.gateway.entitlement.plan;

import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.enums.Plan;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class PlanDefaultsProviderImpl
        implements PlanDefaultsProvider {

    @Override
    public PlanDefaults getDefaults(
            Plan plan) {

        return switch (plan) {

            case FREE -> PlanDefaults.builder()
                    .plan(Plan.FREE)
                    .features(Set.of(
                            Feature.CHAT,
                            Feature.OPENAI,
                            Feature.RATE_LIMITING
                    ))
                    .requestsPerMinute(10)
                    .requestsPerDay(100)
                    .monthlyTokenQuota(100_000)
                    .monthlyBudget(
                            new BigDecimal("5.00"))
                    .build();

            case STARTER -> PlanDefaults.builder()
                    .plan(Plan.STARTER)
                    .features(Set.of(
                            Feature.CHAT,
                            Feature.OPENAI,
                            Feature.GEMINI,
                            Feature.RATE_LIMITING,
                            Feature.QUOTA
                    ))
                    .requestsPerMinute(30)
                    .requestsPerDay(1_000)
                    .monthlyTokenQuota(1_000_000)
                    .monthlyBudget(
                            new BigDecimal("50.00"))
                    .build();

            case PROFESSIONAL -> PlanDefaults.builder()
                    .plan(Plan.PROFESSIONAL)
                    .features(Set.of(
                            Feature.CHAT,
                            Feature.OPENAI,
                            Feature.GEMINI,
                            Feature.CLAUDE,
                            Feature.OLLAMA,
                            Feature.RATE_LIMITING,
                            Feature.QUOTA,
                            Feature.BUDGET
                    ))
                    .requestsPerMinute(100)
                    .requestsPerDay(10_000)
                    .monthlyTokenQuota(10_000_000)
                    .monthlyBudget(
                            new BigDecimal("500.00"))
                    .build();

            case ENTERPRISE -> PlanDefaults.builder()
                    .plan(Plan.ENTERPRISE)
                    .features(Set.of(
                            Feature.CHAT,
                            Feature.OPENAI,
                            Feature.GEMINI,
                            Feature.CLAUDE,
                            Feature.OLLAMA,
                            Feature.RATE_LIMITING,
                            Feature.QUOTA,
                            Feature.BUDGET,
                            Feature.EXTENSIVE_RESEARCH
                    ))
                    .requestsPerMinute(1_000)
                    .requestsPerDay(100_000)
                    .monthlyTokenQuota(100_000_000)
                    .monthlyBudget(
                            new BigDecimal("5000.00"))
                    .build();
        };
    }
}
