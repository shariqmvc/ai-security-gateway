package com.ai.gateway.metrics;

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

        counters.put(MetricsConstants.TOTAL_REQUESTS,
                new AtomicLong());

        counters.put(MetricsConstants.SUCCESSFUL_REQUESTS,
                new AtomicLong());

        counters.put(MetricsConstants.FAILED_REQUESTS,
                new AtomicLong());

        counters.put(MetricsConstants.FIREWALL_BLOCKED,
                new AtomicLong());

        counters.put(MetricsConstants.POLICY_BLOCKED,
                new AtomicLong());

        counters.put(MetricsConstants.OPENAI_REQUESTS,
                new AtomicLong());

        counters.put(MetricsConstants.GEMINI_REQUESTS,
                new AtomicLong());

    }

    public void increment(String metric) {

        counters.get(metric).incrementAndGet();

    }

    public void addLatency(long latency) {

        totalLatency.addAndGet(latency);

    }

    public GatewayMetrics getMetrics() {

        long total =
                counters.get(MetricsConstants.TOTAL_REQUESTS).get();

        long latency =
                totalLatency.get();

        double avg =
                total == 0 ? 0 : (double) latency / total;

        return GatewayMetrics.builder()

                .totalRequests(total)

                .successfulRequests(
                        counters.get(MetricsConstants.SUCCESSFUL_REQUESTS).get())

                .failedRequests(
                        counters.get(MetricsConstants.FAILED_REQUESTS).get())

                .firewallBlockedRequests(
                        counters.get(MetricsConstants.FIREWALL_BLOCKED).get())

                .policyBlockedRequests(
                        counters.get(MetricsConstants.POLICY_BLOCKED).get())

                .openAiRequests(
                        counters.get(MetricsConstants.OPENAI_REQUESTS).get())

                .geminiRequests(
                        counters.get(MetricsConstants.GEMINI_REQUESTS).get())

                .totalLatencyMs(latency)

                .averageLatencyMs(avg)

                .build();

    }
}
