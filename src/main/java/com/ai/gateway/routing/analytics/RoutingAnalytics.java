package com.ai.gateway.routing.analytics;

import java.util.Map;

public record RoutingAnalytics(
        long totalDecisions,
        Map<String, Long> decisionsByStrategy,
        Map<String, Long> decisionsByProvider,
        Map<String, Long> decisionsByProviderModel,
        long failoverAttempts,
        long failoverSuccesses,
        long failoverFailures,
        long intelligentDecisions,
        long unityDecisions,
        Map<String, Long> decisionsByPriority) {

    public RoutingAnalytics(
            long totalDecisions,
            Map<String, Long> decisionsByStrategy,
            Map<String, Long> decisionsByProvider,
            Map<String, Long> decisionsByProviderModel,
            long failoverAttempts,
            long failoverSuccesses,
            long failoverFailures) {
        this(totalDecisions, decisionsByStrategy, decisionsByProvider, decisionsByProviderModel,
                failoverAttempts, failoverSuccesses, failoverFailures, 0, 0, Map.of());
    }
}
