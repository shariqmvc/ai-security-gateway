package com.ai.gateway.metrics;

import com.ai.gateway.enums.Provider;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GatewayMetricsService {

    private final Map<String, AtomicLong> counters =
            new ConcurrentHashMap<>();

    private final AtomicLong totalLatency =
            new AtomicLong();

    public GatewayMetricsService() {

        counters.put(
                MetricsConstants.TOTAL_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.SUCCESSFUL_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.FAILED_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.FIREWALL_BLOCKED,
                new AtomicLong());

        counters.put(
                MetricsConstants.POLICY_BLOCKED,
                new AtomicLong());

        counters.put(
                MetricsConstants.ACCESS_DENIED,
                new AtomicLong());

        counters.put(
                MetricsConstants.OPENAI_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.GEMINI_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.CLAUDE_REQUESTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.OLLAMA_REQUESTS,
                new AtomicLong());

        /*
         * Routing observability
         */
        counters.put(
                MetricsConstants.ROUTING_DECISIONS,
                new AtomicLong());

        counters.put(
                MetricsConstants.ROUTING_EXPLICIT_PROVIDER,
                new AtomicLong());

        counters.put(
                MetricsConstants.ROUTING_EXPLICIT_MODEL,
                new AtomicLong());

        counters.put(
                MetricsConstants.ROUTING_POLICY_BASED,
                new AtomicLong());

        counters.put(
                MetricsConstants.ROUTING_TENANT_DEFAULT,
                new AtomicLong());

        /*
         * Provider failover observability
         */
        counters.put(
                MetricsConstants.ROUTING_FAILOVER_ATTEMPTS,
                new AtomicLong());

        counters.put(
                MetricsConstants.ROUTING_FAILOVER_SUCCESS,
                new AtomicLong());
    }

    public void increment(String metric) {

        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException(
                    "Metric name must not be null or blank.");
        }

        AtomicLong counter = counters.get(metric);

        if (counter == null) {
            throw new IllegalArgumentException(
                    "Unknown gateway metric: " + metric);
        }

        counter.incrementAndGet();
    }

    public void addLatency(long latency) {

        if (latency < 0) {
            throw new IllegalArgumentException(
                    "Latency must not be negative.");
        }

        totalLatency.addAndGet(latency);
    }

    public GatewayMetrics getMetrics() {

        long total =
                counters.get(
                        MetricsConstants.TOTAL_REQUESTS).get();

        long latency =
                totalLatency.get();

        double avg =
                total == 0
                        ? 0
                        : (double) latency / total;

        return GatewayMetrics.builder()

                .totalRequests(total)

                .successfulRequests(
                        counters.get(
                                        MetricsConstants.SUCCESSFUL_REQUESTS)
                                .get())

                .failedRequests(
                        counters.get(
                                        MetricsConstants.FAILED_REQUESTS)
                                .get())

                .firewallBlockedRequests(
                        counters.get(
                                        MetricsConstants.FIREWALL_BLOCKED)
                                .get())

                .policyBlockedRequests(
                        counters.get(
                                        MetricsConstants.POLICY_BLOCKED)
                                .get())

                .openAiRequests(
                        counters.get(
                                        MetricsConstants.OPENAI_REQUESTS)
                                .get())

                .geminiRequests(
                        counters.get(
                                        MetricsConstants.GEMINI_REQUESTS)
                                .get())

                .claudeRequests(
                        counters.get(
                                        MetricsConstants.CLAUDE_REQUESTS)
                                .get())

                .ollamaRequests(
                        counters.get(
                                        MetricsConstants.OLLAMA_REQUESTS)
                                .get())

                .totalLatencyMs(latency)

                .averageLatencyMs(avg)

                .build();
    }

    public void incrementProviderRequest(
            Provider provider) {

        if (provider == null) {
            throw new IllegalArgumentException(
                    "Provider must not be null.");
        }

        switch (provider) {

            case OPENAI ->
                    increment(
                            MetricsConstants.OPENAI_REQUESTS);

            case GEMINI ->
                    increment(
                            MetricsConstants.GEMINI_REQUESTS);

            case CLAUDE ->
                    increment(
                            MetricsConstants.CLAUDE_REQUESTS);

            case OLLAMA ->
                    increment(
                            MetricsConstants.OLLAMA_REQUESTS);
        }
    }
}