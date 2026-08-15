package com.ai.gateway.routing.scoring.impl;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.routing.scoring.CandidateScoringEngine;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CandidateScoringEngineImpl implements CandidateScoringEngine {

    private final List<CandidateScoreStrategy> strategies;
    private final RoutingScoringProperties properties;

    @Override
    public List<ScoredCandidate> score(
            List<RoutingCandidate> candidates,
            CandidateScoringContext context) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        if (context == null) {
            throw new IllegalArgumentException(
                    "Candidate scoring context is required.");
        }

        List<RoutingCandidate> validCandidates = candidates.stream()
                .filter(candidate -> candidate != null)
                .distinct()
                .toList();

        if (validCandidates.isEmpty()) {
            return List.of();
        }

        Map<CandidateScoreDimension, Double> weights = weights(context);

        Map<CandidateScoreDimension, List<Double>> rawValues =
                new EnumMap<>(CandidateScoreDimension.class);

        for (CandidateScoreStrategy strategy : strategies) {
            rawValues.put(
                    strategy.dimension(),
                    validCandidates.stream()
                            .map(candidate -> strategy.rawScore(candidate, context))
                            .toList());
        }

        List<ScoredCandidate> result = new ArrayList<>();

        for (int candidateIndex = 0;
             candidateIndex < validCandidates.size();
             candidateIndex++) {

            RoutingCandidate candidate = validCandidates.get(candidateIndex);
            List<CandidateScoreComponent> components = new ArrayList<>();
            double total = 0.0;

            for (CandidateScoreStrategy strategy : strategies) {
                double rawValue = rawValues
                        .get(strategy.dimension())
                        .get(candidateIndex);

                double normalized = normalize(
                        rawValue,
                        rawValues.get(strategy.dimension()),
                        strategy.lowerIsBetter());

                double weight = weights.getOrDefault(
                        strategy.dimension(),
                        0.0);

                double weighted = normalized * weight;
                total += weighted;

                components.add(
                        new CandidateScoreComponent(
                                strategy.dimension(),
                                rawValue,
                                normalized,
                                weight,
                                weighted));
            }

            result.add(new ScoredCandidate(
                    candidate,
                    components,
                    total));
        }

        return List.copyOf(result);
    }

    private Map<CandidateScoreDimension, Double> weights(CandidateScoringContext context) {
        Map<CandidateScoreDimension, Double> weights =
                new EnumMap<>(CandidateScoreDimension.class);

        RoutingScoringProperties.Weights configured =
                properties.getWeights();

        weights.put(CandidateScoreDimension.COST, configured.getCost());
        weights.put(CandidateScoreDimension.LATENCY, configured.getLatency());
        weights.put(CandidateScoreDimension.AVAILABILITY, configured.getAvailability());
        weights.put(CandidateScoreDimension.POLICY_PREFERENCE,
                configured.getPolicyPreference());

        if (context != null && !context.weightOverrides().isEmpty()) {
            context.weightOverrides().forEach(weights::put);
        }

        for (double weight : weights.values()) {
            if (weight < 0.0 || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalStateException(
                        "Candidate scoring weights must be finite and non-negative.");
            }
        }

        double total = weights.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        if (total <= 0.0) {
            throw new IllegalStateException(
                    "At least one candidate scoring weight must be greater than zero.");
        }

        weights.replaceAll((dimension, weight) -> weight / total);
        return weights;
    }

    private double normalize(
            double rawValue,
            List<Double> values,
            boolean lowerIsBetter) {

        if (Double.isNaN(rawValue) || Double.isInfinite(rawValue)) {
            throw new IllegalStateException(
                    "Candidate scoring strategy produced a non-finite value.");
        }

        double min = values.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(rawValue);

        double max = values.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(rawValue);

        if (Double.compare(min, max) == 0) {
            return 1.0;
        }

        double normalized = lowerIsBetter
                ? (max - rawValue) / (max - min)
                : (rawValue - min) / (max - min);

        return Math.max(0.0, Math.min(1.0, normalized));
    }
}
