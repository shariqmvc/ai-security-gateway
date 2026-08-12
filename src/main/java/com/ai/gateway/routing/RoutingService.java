package com.ai.gateway.routing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutingService {

    private final List<RoutingStrategyHandler>
            strategies;

    public RoutingDecision route(
            RoutingContext context) {

        return strategies.stream()

                .filter(strategy ->
                        strategy.supports(context))

                .findFirst()

                .orElseThrow(() ->
                        new IllegalStateException(
                                "No routing strategy available."))

                .route(context);
    }
}
