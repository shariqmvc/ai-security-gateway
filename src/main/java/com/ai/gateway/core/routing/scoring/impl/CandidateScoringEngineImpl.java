package com.ai.gateway.core.routing.scoring.impl;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.core.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;
import com.ai.gateway.core.routing.scoring.CandidateScoringEngine;
import com.ai.gateway.core.routing.scoring.ScoredCandidate;
import com.ai.gateway.core.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.ai.gateway.core.routing.scoring.objective.RoutingObjective;
import com.ai.gateway.core.routing.scoring.objective.RoutingObjectiveVector;
import com.ai.gateway.core.routing.scoring.objective.RoutingUtilityCalculator;
import com.ai.gateway.core.routing.scoring.objective.RoutingUtilityResult;

/**
 * Deterministic scoring with O(S*C) normalization rather than O(S*C^2).
 * S = number of scoring strategies, C = candidate count.
 */
@Service
@RequiredArgsConstructor
public class CandidateScoringEngineImpl implements CandidateScoringEngine {

    private final List<CandidateScoreStrategy> strategies;
    private final RoutingScoringProperties properties;
    private final RoutingUtilityCalculator utilityCalculator = new RoutingUtilityCalculator();

    @Override
    public List<ScoredCandidate> score(List<RoutingCandidate> candidates, CandidateScoringContext context) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (context == null) throw new IllegalArgumentException("Candidate scoring context is required.");

        List<RoutingCandidate> validCandidates = new ArrayList<>(candidates.size());
        java.util.HashSet<RoutingCandidate> seen = new java.util.HashSet<>(Math.max(16, candidates.size() * 2));
        for (RoutingCandidate candidate : candidates) {
            if (candidate != null && seen.add(candidate)) validCandidates.add(candidate);
        }
        if (validCandidates.isEmpty()) return List.of();

        Map<CandidateScoreDimension, Double> weights = weights(context);
        int candidateCount = validCandidates.size();

        EnumMap<CandidateScoreDimension, double[]> rawValues =
                new EnumMap<>(CandidateScoreDimension.class);
        EnumMap<CandidateScoreDimension, Double> minima =
                new EnumMap<>(CandidateScoreDimension.class);
        EnumMap<CandidateScoreDimension, Double> maxima =
                new EnumMap<>(CandidateScoreDimension.class);

        // Compute each strategy's raw values and min/max once.
        for (CandidateScoreStrategy strategy : strategies) {
            double[] values = new double[candidateCount];
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < candidateCount; i++) {
                double raw = strategy.rawScore(validCandidates.get(i), context);
                if (Double.isNaN(raw) || Double.isInfinite(raw)) {
                    throw new IllegalStateException("Candidate scoring strategy produced a non-finite value.");
                }
                values[i] = raw;
                min = Math.min(min, raw);
                max = Math.max(max, raw);
            }
            rawValues.put(strategy.dimension(), values);
            minima.put(strategy.dimension(), min);
            maxima.put(strategy.dimension(), max);
        }

        List<ScoredCandidate> result = new ArrayList<>(candidateCount);
        for (int i = 0; i < candidateCount; i++) {
            RoutingCandidate candidate = validCandidates.get(i);
            List<CandidateScoreComponent> components = new ArrayList<>(strategies.size());
            double total = 0.0;

            for (CandidateScoreStrategy strategy : strategies) {
                CandidateScoreDimension dimension = strategy.dimension();
                double raw = rawValues.get(dimension)[i];
                double min = minima.get(dimension);
                double max = maxima.get(dimension);
                double normalized = normalize(raw, min, max, strategy.lowerIsBetter());
                double weight = weights.getOrDefault(dimension, 0.0);
                double weighted = normalized * weight;
                total += weighted;
                components.add(new CandidateScoreComponent(dimension, raw, normalized, weight, weighted));
            }

            if (context.objectiveWeights() != null) {
                RoutingObjectiveVector vector = objectiveVector(components);
                RoutingUtilityResult utility = utilityCalculator.calculate(
                        vector, context.objectiveWeights());
                total = utility.utility();

                List<CandidateScoreComponent> utilityComponents = new ArrayList<>(components.size());
                for (CandidateScoreComponent component : components) {
                    RoutingObjective objective = toObjective(component.dimension());
                    double effectiveWeight = utility.effectiveWeightOf(objective);
                    double weighted = component.normalizedScore() * effectiveWeight;
                    utilityComponents.add(new CandidateScoreComponent(
                            component.dimension(),
                            component.rawValue(),
                            component.normalizedScore(),
                            effectiveWeight,
                            weighted));
                }
                components = utilityComponents;
            }

            result.add(new ScoredCandidate(candidate, components, total));
        }

        return List.copyOf(result);
    }

    private RoutingObjectiveVector objectiveVector(List<CandidateScoreComponent> components) {
        EnumMap<RoutingObjective, Double> values = new EnumMap<>(RoutingObjective.class);
        for (CandidateScoreComponent component : components) {
            values.put(toObjective(component.dimension()), component.normalizedScore());
        }
        return new RoutingObjectiveVector(values);
    }

    private RoutingObjective toObjective(CandidateScoreDimension dimension) {
        return switch (dimension) {
            case COST -> RoutingObjective.COST;
            case LATENCY -> RoutingObjective.LATENCY;
            case AVAILABILITY -> RoutingObjective.AVAILABILITY;
            case POLICY_PREFERENCE -> RoutingObjective.POLICY_PREFERENCE;
        };
    }

    private Map<CandidateScoreDimension, Double> weights(CandidateScoringContext context) {
        EnumMap<CandidateScoreDimension, Double> weights = new EnumMap<>(CandidateScoreDimension.class);
        RoutingScoringProperties.Weights configured = properties.getWeights();
        weights.put(CandidateScoreDimension.COST, configured.getCost());
        weights.put(CandidateScoreDimension.LATENCY, configured.getLatency());
        weights.put(CandidateScoreDimension.AVAILABILITY, configured.getAvailability());
        weights.put(CandidateScoreDimension.POLICY_PREFERENCE, configured.getPolicyPreference());
        if (context != null && !context.weightOverrides().isEmpty()) context.weightOverrides().forEach(weights::put);

        double total = 0.0;
        for (double weight : weights.values()) {
            if (weight < 0.0 || Double.isNaN(weight) || Double.isInfinite(weight)) {
                throw new IllegalStateException("Candidate scoring weights must be finite and non-negative.");
            }
            total += weight;
        }
        if (total <= 0.0) throw new IllegalStateException("At least one candidate scoring weight must be greater than zero.");
        double finalTotal = total;
        weights.replaceAll((dimension, weight) -> weight / finalTotal);
        return weights;
    }

    private double normalize(double rawValue, double min, double max, boolean lowerIsBetter) {
        if (Double.compare(min, max) == 0) return 1.0;
        double normalized = lowerIsBetter
                ? (max - rawValue) / (max - min)
                : (rawValue - min) / (max - min);
        return Math.max(0.0, Math.min(1.0, normalized));
    }
}
