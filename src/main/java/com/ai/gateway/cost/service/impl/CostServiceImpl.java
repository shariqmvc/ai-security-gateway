package com.ai.gateway.cost.service.impl;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.budget.service.BudgetService;
import com.ai.gateway.cost.dto.CostRequest;
import com.ai.gateway.cost.dto.CostResponse;
import com.ai.gateway.cost.dto.CostSummary;
import com.ai.gateway.cost.entity.RequestCost;
import com.ai.gateway.cost.repository.RequestCostRepository;
import com.ai.gateway.cost.service.CostCalculator;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostServiceImpl implements CostService {

    private final CostCalculator costCalculator;

    private final RequestCostRepository requestCostRepository;
    private final BudgetService budgetService;

    private final TenantSchemaRoutingService tenantSchemaRoutingService;
    private final TenantAccessGuard tenantAccessGuard;

    @Override
    @Transactional
    public BigDecimal save(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse) {

        BigDecimal totalCost = enforceBudget(requestId, context, aiRequest, aiResponse);
        persist(requestId, context, aiRequest, aiResponse);
        return totalCost;
    }

    @Override
    @Transactional
    public BigDecimal enforceBudget(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse) {

        if (context == null || context.getTenantId() == null) {
            throw new IllegalStateException(
                    "Authenticated tenant context is required for cost persistence.");
        }

        tenantAccessGuard.requireAccess(context.getTenantId());

        CostResponse response = calculate(aiRequest, aiResponse);
        BigDecimal totalCost = response.getTotalCost();

        // Budget enforcement is a governance control and therefore remains
        // synchronous. Only the non-critical persistence is moved async.
        budgetService.consume(context.getTenantId(), totalCost);

        return totalCost;
    }

    @Override
    @Transactional
    public void persist(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse) {

        if (context == null || context.getTenantId() == null) {
            throw new IllegalStateException(
                    "Authenticated tenant context is required for cost persistence.");
        }

        tenantAccessGuard.requireAccess(context.getTenantId());
        tenantSchemaRoutingService.useTenantSchema();

        Usage usage = aiResponse.getUsage();
        if (usage == null
                || usage.getInputTokens() == null
                || usage.getOutputTokens() == null) {
            return;
        }

        CostResponse response = calculate(aiRequest, aiResponse);

        RequestCost entity =
                RequestCost.builder()
                        .requestId(requestId)
                        .tenantId(context.getTenantId())
                        .provider(aiRequest.getProvider())
                        .model(aiRequest.getModel())
                        .inputTokens(usage.getInputTokens())
                        .outputTokens(usage.getOutputTokens())
                        .totalTokens(usage.getTotalTokens())
                        .inputCost(response.getInputCost())
                        .outputCost(response.getOutputCost())
                        .totalCost(response.getTotalCost())
                        .reasoningTokens(usage.getReasoningTokens())
                        .createdAt(LocalDateTime.now())
                        .build();
        requestCostRepository.save(entity);
    }

    private CostResponse calculate(
            AIRequest aiRequest,
            AIResponse aiResponse) {

        Usage usage = aiResponse.getUsage();
        if (usage == null
                || usage.getInputTokens() == null
                || usage.getOutputTokens() == null) {
            return CostResponse.builder()
                    .inputCost(BigDecimal.ZERO)
                    .outputCost(BigDecimal.ZERO)
                    .totalCost(BigDecimal.ZERO)
                    .build();
        }

        CostRequest request =
                CostRequest.builder()
                        .provider(aiRequest.getProvider())
                        .model(aiRequest.getModel())
                        .inputTokens(usage.getInputTokens())
                        .outputTokens(usage.getOutputTokens())
                        .build();

        return costCalculator.calculate(request);
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getOverallSummary() {

        tenantSchemaRoutingService.useTenantSchema();
        return requestCostRepository.getOverallSummary();
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getTenantSummary(UUID tenantId) {

        tenantAccessGuard.requireAccess(tenantId);
        tenantSchemaRoutingService.useTenantSchema();

        // The path variable is safe to use after requireAccess() because
        // requireAccess() has already established that it exactly matches
        // the authenticated tenant. Do not resolve the tenant a second time
        // through an independent ThreadLocal lookup; doing so makes this
        // service unnecessarily dependent on request-context state and can
        // make unit/background tests appear to query a null tenant.
        return requestCostRepository.getTenantSummary(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getProviderSummary(
            Provider provider) {

        tenantSchemaRoutingService.useTenantSchema();
        return requestCostRepository.getProviderSummary(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getModelSummary(String model) {
        tenantSchemaRoutingService.useTenantSchema();
        return requestCostRepository.getModelSummary(model);
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getTodaySummary() {
        tenantSchemaRoutingService.useTenantSchema();
        LocalDate today = LocalDate.now();

        return requestCostRepository.getSummaryBetween(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
    }

    @Override
    @Transactional(readOnly = true)
    public CostSummary getMonthlySummary() {
        tenantSchemaRoutingService.useTenantSchema();
        LocalDate firstDay =
                LocalDate.now()
                        .withDayOfMonth(1);

        return requestCostRepository.getSummaryBetween(
                firstDay.atStartOfDay(),
                firstDay.plusMonths(1).atStartOfDay());
    }

}
