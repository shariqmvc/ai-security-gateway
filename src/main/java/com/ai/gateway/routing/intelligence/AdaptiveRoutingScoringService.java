package com.ai.gateway.routing.intelligence;

import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.Map;

/** Deterministic, policy-driven weight adaptation. No ML/LLM decision maker is involved. */
@Service
@RequiredArgsConstructor
public class AdaptiveRoutingScoringService {

    private final RoutingScoringProperties properties;

    public Map<CandidateScoreDimension, Double> adapt(RoutingDecisionContext context) {
        RoutingScoringProperties.Weights w = properties.getWeights();
        Map<CandidateScoreDimension, Double> result = new EnumMap<>(CandidateScoreDimension.class);
        result.put(CandidateScoreDimension.COST, w.getCost());
        result.put(CandidateScoreDimension.LATENCY, w.getLatency());
        result.put(CandidateScoreDimension.AVAILABILITY, w.getAvailability());
        result.put(CandidateScoreDimension.POLICY_PREFERENCE, w.getPolicyPreference());

        if (context != null) {
            switch (context.routingPriority()) {
                case COST -> boost(result, CandidateScoreDimension.COST, 1.35);
                case LATENCY -> boost(result, CandidateScoreDimension.LATENCY, 1.35);
                case RELIABILITY -> boost(result, CandidateScoreDimension.AVAILABILITY, 1.35);
                case BALANCED -> { }
            }
            if (context.extensiveResearchEnabled()) {
                // Research may spend more for robustness, but cost remains a soft signal.
                boost(result, CandidateScoreDimension.AVAILABILITY, 1.20);
                boost(result, CandidateScoreDimension.POLICY_PREFERENCE, 1.10);
            }
        }

        normalize(result);
        return Map.copyOf(result);
    }

    private void boost(Map<CandidateScoreDimension, Double> values, CandidateScoreDimension dimension, double factor) {
        values.computeIfPresent(dimension, (k, v) -> v * factor);
    }

    private void normalize(Map<CandidateScoreDimension, Double> values) {
        double total = values.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) throw new IllegalStateException("Adaptive routing weights must contain a positive total.");
        values.replaceAll((k, v) -> v / total);
    }
}
