package com.ai.gateway.budget.service;

import com.ai.gateway.budget.dto.BudgetUsageResponse;

import java.math.BigDecimal;
import java.util.UUID;

public interface BudgetService {
    void consume(
            UUID tenantId,
            BigDecimal amount);

    BudgetUsageResponse getUsage(
            UUID tenantId);
}
