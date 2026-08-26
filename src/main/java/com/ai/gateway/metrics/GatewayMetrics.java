package com.ai.gateway.metrics;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewayMetrics {

    private long totalRequests;

    private long successfulRequests;

    private long failedRequests;

    private long firewallBlockedRequests;

    private long policyBlockedRequests;

    private long inferenceCacheHits;

    private long inferenceCacheMisses;

    private long openAiRequests;

    private long geminiRequests;

    private long totalLatencyMs;

    private double averageLatencyMs;
    private long claudeRequests;

    private long ollamaRequests;

    private long routingFailoverAttempts;
    private long routingFailoverSuccess;
    private long routingFailoverCircuitOpen;
    private long routingFailoverBudgetExhausted;


}