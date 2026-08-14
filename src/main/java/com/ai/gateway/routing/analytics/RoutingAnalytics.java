package com.ai.gateway.routing.analytics;

import java.util.Map;

public record RoutingAnalytics(
        long totalDecisions,
        Map<String, Long> decisionsByStrategy,
        Map<String, Long> decisionsByProvider,
        Map<String, Long> decisionsByProviderModel,
        long failoverAttempts,
        long failoverSuccesses,
        long failoverFailures
) {
}