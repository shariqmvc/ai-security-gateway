package com.ai.gateway.core.routing.scoring.strategy;

import com.ai.gateway.core.cost.config.PricingConfig;
import com.ai.gateway.core.cost.dto.ModelPricing;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.core.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CostScoreStrategy implements CandidateScoreStrategy {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final PricingConfig pricingConfig;

    @Override
    public CandidateScoreDimension dimension() {
        return CandidateScoreDimension.COST;
    }

    @Override
    public double rawScore(
            RoutingCandidate candidate,
            CandidateScoringContext context) {

        ModelPricing pricing = pricingConfig.getPricing(
                candidate.provider(),
                candidate.model());

        BigDecimal inputCost = BigDecimal.valueOf(
                        context.estimatedInputTokens())
                .multiply(pricing.getInputPricePerMillionTokens())
                .divide(ONE_MILLION, 12, java.math.RoundingMode.HALF_UP);

        BigDecimal outputCost = BigDecimal.valueOf(
                        context.estimatedOutputTokens())
                .multiply(pricing.getOutputPricePerMillionTokens())
                .divide(ONE_MILLION, 12, java.math.RoundingMode.HALF_UP);

        return inputCost.add(outputCost).doubleValue();
    }

    @Override
    public boolean lowerIsBetter() {
        return true;
    }
}
