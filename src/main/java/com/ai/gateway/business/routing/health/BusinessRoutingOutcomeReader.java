package com.ai.gateway.business.routing.health;

import com.ai.gateway.business.routing.health.entity.RoutingOutcome;
import com.ai.gateway.business.routing.health.repository.RoutingOutcomeRepository;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.health.RoutingOutcomeReader;
import com.ai.gateway.core.routing.health.RoutingOutcomeSample;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Business persistence adapter for the product-neutral Core health reader. */
@Component
@RequiredArgsConstructor
public class BusinessRoutingOutcomeReader implements RoutingOutcomeReader {

    private final RoutingOutcomeRepository repository;

    @Override
    public List<RoutingOutcomeSample> findRecent(Provider provider, String model) {
        return repository
                .findTop100ByProviderAndModelOrderByCreatedAtDesc(provider, model)
                .stream()
                .filter(outcome -> outcome.getLatencyMs() != null && outcome.getLatencyMs() >= 0)
                .map(outcome -> new RoutingOutcomeSample(
                        outcome.getLatencyMs(),
                        outcome.isSuccess()))
                .toList();
    }
}
