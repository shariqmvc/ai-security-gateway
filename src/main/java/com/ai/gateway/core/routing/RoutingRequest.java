package com.ai.gateway.core.routing;

import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Canonical routing input. It normalizes the routing-relevant portion of a
 * ChatRequest without replacing ChatRequest as the gateway's execution DTO.
 */
public record RoutingRequest(
        String model,
        Provider provider,
        Set<String> requiredCapabilities,
        ProviderPreferences providerPreferences,
        String optimizationProfile,
        String selectionMode,
        int topN,
        String escalationProfile,
        BigDecimal maximumRequestCost,
        BigDecimal remainingWorkflowBudget) {

    public RoutingRequest {
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        providerPreferences = providerPreferences == null ? ProviderPreferences.defaults() : providerPreferences;
        selectionMode = selectionMode == null || selectionMode.isBlank() ? "SINGLE" : selectionMode;
        topN = topN < 1 ? 1 : topN;
    }

    public static RoutingRequest from(ChatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Chat request is required.");
        }
        ProviderPreferences preferences = request.getProvider() == null
                ? ProviderPreferences.defaults()
                : new ProviderPreferences(java.util.List.of(request.getProvider()), Set.of(), true);
        return new RoutingRequest(
                request.getModel(),
                request.getProvider(),
                request.getRequiredCapabilities(),
                preferences,
                request.getRoutingOptimizationProfile(),
                request.getRoutingSelectionMode(),
                request.getRoutingTopN(),
                request.getRoutingEscalationProfile(),
                request.getMaximumRequestCost(),
                request.getRemainingWorkflowBudget());
    }
}
