package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/**
 * 6.7 deterministic optimization layer. It never expands the candidate set
 * and never overrides governance; it only adjusts soft scoring weights from
 * fresh runtime signals and applies candidate-specific health adjustments.
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

        /*
         * Runtime health is intentionally NOT converted into a global weight
         * boost. A degraded endpoint must affect that endpoint, not every
         * candidate in the routing set. Candidate-specific health adjustment
         * is applied by optimizeCandidate().
         */

        if (signals != null && !signals.latencyMs().isEmpty()
                && priority == RoutingPriority.LATENCY) {
            boost(result, CandidateScoreDimension.LATENCY,
                    properties.getLatencyPriorityBoost());
        }

        if (priority == RoutingPriority.COST) {
            boost(result, CandidateScoreDimension.COST,
                    properties.getCostPriorityBoost());
        }

        if (priority == RoutingPriority.RELIABILITY) {
            boost(result, CandidateScoreDimension.AVAILABILITY,
                    properties.getReliabilityPriorityBoost());
        }

        normalize(result);
        return Map.copyOf(result);
    }

    /**
     * Computes an individual score adjustment from the candidate's own
     * runtime availability. Sparse telemetry is deliberately ignored.
     */
    public RoutingCandidateOptimization optimizeCandidate(
            RoutingCandidate candidate,
            RoutingRuntimeSignals signals) {

        if (!properties.isEnabled() || candidate == null || signals == null) {
            return RoutingCandidateOptimization.neutral();
        }

        String key = candidate.candidateKey();
        String legacyKey = candidate.provider().name() + ":" + candidate.model();

        Double availability = firstPresent(
                signals.availability().get(key),
                signals.availability().get(legacyKey));

        long observations = firstPresent(
                signals.observations().get(key),
                signals.observations().get(legacyKey),
                0L);

        if (availability == null
                || observations < Math.max(0L, properties.getMinObservations())) {
            return RoutingCandidateOptimization.neutral();
        }

        double boundedAvailability =
                Math.max(0.0, Math.min(1.0, availability));

        if (boundedAvailability < 0.70) {
            return new RoutingCandidateOptimization(
                    properties.getUnhealthyCandidateScoreMultiplier(),
                    "UNHEALTHY_AVAILABILITY");
        }

        if (boundedAvailability < 0.90) {
            return new RoutingCandidateOptimization(
                    properties.getDegradedCandidateScoreMultiplier(),
                    "DEGRADED_AVAILABILITY");
        }

        return RoutingCandidateOptimization.neutral();
    }

    private <T> T firstPresent(T primary, T secondary) {
        return primary != null ? primary : secondary;
    }

    private long firstPresent(Long primary, Long secondary, long fallback) {
        if (primary != null) return primary;
        if (secondary != null) return secondary;
        return fallback;
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
