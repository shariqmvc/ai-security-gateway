package com.ai.gateway.cost.service.impl;

import com.ai.gateway.authentication.AuthenticationContext;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostServiceImpl implements CostService {

    private final CostCalculator costCalculator;

    private final RequestCostRepository requestCostRepository;

    @Override
    public void save(
            UUID requestId,
            AuthenticationContext context,
            AIRequest aiRequest,
            AIResponse aiResponse) {

        Usage usage = aiResponse.getUsage();

        if (usage == null
                || usage.getInputTokens() == null
                || usage.getOutputTokens() == null) {

            log.debug("Token usage unavailable. Skipping cost calculation.");
            return;
        }


        CostRequest request =
                CostRequest.builder()
                        .provider(aiRequest.getProvider())
                        .model(aiRequest.getModel())
                        .inputTokens(
                                aiResponse.getUsage().getInputTokens())
                        .outputTokens(
                                aiResponse.getUsage().getOutputTokens())
                        .build();

        CostResponse response =
                costCalculator.calculate(request);

        RequestCost entity =
                RequestCost.builder()
                        .requestId(requestId)
                        .tenantId(context.getTenantId())
                        .provider(aiRequest.getProvider())
                        .model(aiRequest.getModel())
                        .inputTokens(
                                aiResponse.getUsage().getInputTokens())
                        .outputTokens(
                                aiResponse.getUsage().getOutputTokens())
                        .inputCost(response.getInputCost())
                        .outputCost(response.getOutputCost())
                        .totalCost(response.getTotalCost())
                        .createdAt(LocalDateTime.now())
                        .build();

        requestCostRepository.save(entity);
    }

    @Override
    public CostSummary getOverallSummary() {

        return requestCostRepository.getOverallSummary();
    }

    @Override
    public CostSummary getTenantSummary(UUID tenantId) {

        return requestCostRepository.getTenantSummary(tenantId);
    }

    @Override
    public CostSummary getProviderSummary(
            Provider provider) {

        return requestCostRepository.getProviderSummary(provider);
    }

    @Override
    public CostSummary getModelSummary(String model) {
        return requestCostRepository.getModelSummary(model);
    }

    @Override
    public CostSummary getTodaySummary() {
        LocalDate today = LocalDate.now();

        return requestCostRepository.getSummaryBetween(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
    }

    @Override
    public CostSummary getMonthlySummary() {
        LocalDate firstDay =
                LocalDate.now()
                        .withDayOfMonth(1);

        return requestCostRepository.getSummaryBetween(
                firstDay.atStartOfDay(),
                firstDay.plusMonths(1).atStartOfDay());
    }

}
