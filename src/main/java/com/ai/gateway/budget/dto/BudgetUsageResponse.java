package com.ai.gateway.budget.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetUsageResponse {

    private UUID tenantId;

    private BigDecimal used;

    private BigDecimal limit;

    private BigDecimal remaining;
}
