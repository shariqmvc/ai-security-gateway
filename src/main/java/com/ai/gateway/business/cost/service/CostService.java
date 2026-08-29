package com.ai.gateway.business.cost.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.cost.dto.CostSummary;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.model.Provider;

import java.math.BigDecimal;
import java.util.UUID;

public interface CostService {

    BigDecimal save(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse);

    /**
     * Performs the synchronous budget guardrail without persisting the final
     * request-cost row. The guardrail remains on the request critical path.
     */
    BigDecimal enforceBudget(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse);

    /**
     * Persists the already-authorized actual cost. This operation is safe to
     * execute asynchronously after the HTTP response is ready.
     */
    void persist(
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
