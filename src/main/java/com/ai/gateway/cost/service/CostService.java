package com.ai.gateway.cost.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.dto.CostSummary;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;

import java.util.UUID;

public interface CostService {

    void save(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse);

    CostSummary getOverallSummary();

    CostSummary getTenantSummary(UUID tenantId);

    CostSummary getProviderSummary(Provider provider);

    CostSummary getModelSummary(String model);

    CostSummary getTodaySummary();

    CostSummary getMonthlySummary();

}
