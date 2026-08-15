package com.ai.gateway.routing.selection.impl;

import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.selection.CandidateSelectionEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic candidate selection.
 *
 * <p>The primary ordering is aggregate score. Ties are resolved by policy
 * preference, then by the original candidate order. Java's stable sort
 * therefore gives us a reproducible final tie-break without inventing a
 * provider/model preference that is not part of the routing policy.</p>
 */
@Service
public class CandidateSelectionEngineImpl implements CandidateSelectionEngine {

    private static final double SCORE_EPSILON = 1.0e-9;

    @Override
    public ScoredCandidate select(List<ScoredCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one scored candidate is required.");
        }

        return rank(candidates).get(0);
    }

    /**
     * Returns candidates in deterministic winner-first order.
     */
    public List<ScoredCandidate> rank(List<ScoredCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<IndexedCandidate> indexed = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            ScoredCandidate candidate = candidates.get(i);
            if (candidate != null) {
                indexed.add(new IndexedCandidate(candidate, i));
            }
        }

        indexed.sort((left, right) -> {
            int scoreComparison = compareDescending(
                    left.candidate().totalScore(),
                    right.candidate().totalScore());

            if (Math.abs(
                    left.candidate().totalScore()
                            - right.candidate().totalScore())
                    > SCORE_EPSILON) {
                return scoreComparison;
            }

            int policyPreferenceComparison = compareDescending(
                    normalized(left.candidate(), CandidateScoreDimension.POLICY_PREFERENCE),
                    normalized(right.candidate(), CandidateScoreDimension.POLICY_PREFERENCE));

            if (policyPreferenceComparison != 0) {
                return policyPreferenceComparison;
            }

            // Stable, deterministic tie-break: preserve resolver order.
            return Integer.compare(left.originalIndex(), right.originalIndex());
        });

        return indexed.stream()
                .map(IndexedCandidate::candidate)
                .toList();
    }

    private int compareDescending(double left, double right) {
        return Double.compare(right, left);
    }

    private double normalized(
            ScoredCandidate candidate,
            CandidateScoreDimension dimension) {

        return candidate.components().stream()
                .filter(component -> component.dimension() == dimension)
                .mapToDouble(CandidateScoreComponent::normalizedScore)
                .findFirst()
                .orElse(0.0);
    }

    private record IndexedCandidate(
            ScoredCandidate candidate,
            int originalIndex) {
    }
}
