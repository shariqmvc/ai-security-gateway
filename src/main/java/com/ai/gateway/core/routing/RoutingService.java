package com.ai.gateway.core.routing;

import com.ai.gateway.exception.BusinessException;
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

        validateContext(context);

        return strategies.stream()

                .filter(strategy ->
                        strategy.supports(context))

                .findFirst()

                .orElseThrow(() ->
                        new BusinessException(
                                "No routing strategy available."))

                .route(context);
    }

    private void validateContext(
            RoutingContext context) {

        if (context == null) {

            throw new BusinessException(
                    "Routing context is required.");
        }

        if (context.request() == null) {

            throw new BusinessException(
                    "Chat request is required for routing.");
        }

        if (context.authenticationContext() == null) {

            throw new BusinessException(
                    "Authentication context is required for routing.");
        }
    }
}
