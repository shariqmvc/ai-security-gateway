package com.ai.gateway.business.cost;

import com.ai.gateway.core.cost.dto.CostSummary;
import com.ai.gateway.business.cost.service.CostService;
import com.ai.gateway.core.model.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    @GetMapping("/summary")
    public CostSummary summary() {

        return costService.getOverallSummary();

    }

    @GetMapping("/provider/{provider}")
    public CostSummary provider(
            @PathVariable Provider provider) {

        return costService.getProviderSummary(provider);

    }

    @GetMapping("/tenant/{tenantId}")
    public CostSummary tenant(
            @PathVariable UUID tenantId) {

        return costService.getTenantSummary(tenantId);

    }

    @GetMapping("/model/{model}")
    public CostSummary getModelSummary(
            @PathVariable String model) {

        return costService.getModelSummary(model);

    }

    @GetMapping("/today")
    public CostSummary today() {

        return costService.getTodaySummary();

    }

    @GetMapping("/month")
    public CostSummary month() {

        return costService.getMonthlySummary();

    }

}
