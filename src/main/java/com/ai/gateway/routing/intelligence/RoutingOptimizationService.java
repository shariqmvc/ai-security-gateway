package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 6.7 deterministic optimization layer. It never expands the candidate set
 * and never overrides governance; it only adjusts soft scoring weights from
 * fresh runtime signals and configured optimization policy.
 */
@Service
public class RoutingOptimizationService {

    @Autowired(required = false)
    private RoutingOptimizationProperties properties = new RoutingOptimizationProperties();

    public Map<CandidateScoreDimension, Double> optimize(
            Map<CandidateScoreDimension, Double> baseWeights,
            RoutingRuntimeSignals signals,
            RoutingPriority priority) {

        Map<CandidateScoreDimension, Double> result =
                new EnumMap<>(CandidateScoreDimension.class);
        result.putAll(baseWeights);

        if (!properties.isEnabled()) {
            return Map.copyOf(result);
        }

        if (signals != null && !signals.availability().isEmpty()) {
            double minAvailability = signals.availability().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .min().orElse(1.0);

            if (minAvailability < 0.90) {
                boost(result, CandidateScoreDimension.AVAILABILITY,
                        properties.getDegradedAvailabilityBoost());
            }
            if (minAvailability < 0.70) {
                boost(result, CandidateScoreDimension.AVAILABILITY,
                        properties.getUnhealthyAvailabilityBoost());
            }
        }

        if (signals != null && !signals.latencyMs().isEmpty()
                && priority == RoutingPriority.LATENCY) {
            boost(result, CandidateScoreDimension.LATENCY,
                    properties.getLatencyPriorityBoost());
        }

        if (priority == RoutingPriority.COST) {
            boost(
                    result,
                    CandidateScoreDimension.COST,
                    properties.getCostPriorityBoost()
            );
        }

        if (priority == RoutingPriority.RELIABILITY) {
            boost(
                    result,
                    CandidateScoreDimension.AVAILABILITY,
                    properties.getReliabilityPriorityBoost()
            );
        }

        normalize(result);
        return Map.copyOf(result);
    }

    private void boost(Map<CandidateScoreDimension, Double> values,
                       CandidateScoreDimension dimension,
                       double factor) {
        if (factor <= 0.0 || Double.isNaN(factor) || Double.isInfinite(factor)) {
            throw new IllegalStateException("Routing optimization boost must be finite and positive.");
        }
        values.computeIfPresent(dimension, (k, v) -> v * factor);
    }

    private void normalize(Map<CandidateScoreDimension, Double> values) {
        double total = values.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        if (total <= 0.0) {
            throw new IllegalStateException("Routing optimization weights must be positive.");
        }
        values.replaceAll((k, v) -> v / total);
    }
}
