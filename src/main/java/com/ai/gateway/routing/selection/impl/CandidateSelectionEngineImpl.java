package com.ai.gateway.routing.selection.impl;

import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.selection.CandidateSelectionEngine;
import com.ai.gateway.routing.selection.CandidateSelectionExplanation;
import com.ai.gateway.routing.selection.CandidateSelectionResult;
import com.ai.gateway.routing.selection.RoutingSelectionMode;
import com.ai.gateway.routing.selection.RoutingSelectionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Deterministic candidate selection with bounded ranking optimization.
 *
 * <p>Phase A optimizes the final selection stage without changing the routing
 * semantics of hard constraints or scoring. Candidates that are Pareto
 * dominated can be removed safely because every score dimension is normalized
 * to "higher is better" and the aggregate score uses non-negative weights.
 * The remaining candidates are reduced to a deterministic Top-K using a
 * bounded heap, avoiding a full O(C log C) sort when only the best K are
 * required.</p>
 */
@Service
public class CandidateSelectionEngineImpl implements CandidateSelectionEngine {

    private static final double SCORE_EPSILON = 1.0e-9;
    private static final int DEFAULT_TOP_K = 5;
    private static final int DEFAULT_PARETO_MAX_CANDIDATES = 64;

    private final int topK;
    private final boolean paretoEnabled;
    private final int paretoMaxCandidates;

    public CandidateSelectionEngineImpl() {
        this(DEFAULT_TOP_K, true, DEFAULT_PARETO_MAX_CANDIDATES);
    }

    @Autowired
    public CandidateSelectionEngineImpl(
            com.ai.gateway.routing.intelligence.RoutingOptimizationProperties properties) {
        this(
                properties.getTopK(),
                properties.isParetoEnabled(),
                properties.getParetoMaxCandidates());
    }

    public CandidateSelectionEngineImpl(
            int topK,
            boolean paretoEnabled,
            int paretoMaxCandidates) {
        this.topK = topK;
        this.paretoEnabled = paretoEnabled;
        this.paretoMaxCandidates = paretoMaxCandidates;
    }

    @Override
    public CandidateSelectionResult selectWithRanking(List<ScoredCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one scored candidate is required.");
        }

        List<ScoredCandidate> validCandidates = nonNullCandidates(candidates);
        if (validCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one non-null scored candidate is required.");
        }

        return select(validCandidates, RoutingSelectionRequest.single());
    }

    @Override
    public CandidateSelectionResult select(
            List<ScoredCandidate> candidates,
            RoutingSelectionRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Routing selection request is required.");
        }
        List<ScoredCandidate> validCandidates = nonNullCandidates(candidates);
        if (validCandidates.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one non-null scored candidate is required.");
        }

        List<ScoredCandidate> optimized = optimizeCandidates(validCandidates);
        List<ScoredCandidate> ranked = rank(optimized);
        if (ranked.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one non-null scored candidate is required.");
        }

        return switch (request.mode()) {
            case SINGLE -> singleResult(ranked);
            case TOP_N -> topNResult(ranked, request.topN());
            case PRIMARY_ESCALATION -> primaryEscalationResult(ranked);
        };
    }

    private CandidateSelectionResult singleResult(List<ScoredCandidate> ranked) {
        ScoredCandidate selected = ranked.get(0);
        boolean tie = ranked.size() > 1
                && Math.abs(selected.totalScore() - ranked.get(1).totalScore()) <= SCORE_EPSILON;
        String criterion = tie ? tieBreakCriterion(selected, ranked.get(1)) : null;
        return new CandidateSelectionResult(
                selected,
                ranked,
                List.of(selected),
                new CandidateSelectionExplanation(
                        RoutingSelectionMode.SINGLE,
                        tie ? "DETERMINISTIC_TIE_BREAK" : "HIGHEST_UTILITY",
                        tie,
                        criterion));
    }

    private CandidateSelectionResult topNResult(
            List<ScoredCandidate> ranked,
            int requestedTopN) {
        int limit = Math.min(requestedTopN, ranked.size());
        List<ScoredCandidate> selected = List.copyOf(ranked.subList(0, limit));
        return new CandidateSelectionResult(
                selected.get(0),
                ranked,
                selected,
                new CandidateSelectionExplanation(
                        RoutingSelectionMode.TOP_N,
                        "TOP_N_UTILITY",
                        false,
                        null));
    }

    private CandidateSelectionResult primaryEscalationResult(List<ScoredCandidate> ranked) {
        List<ScoredCandidate> selected = List.copyOf(ranked.subList(0, Math.min(2, ranked.size())));
        return new CandidateSelectionResult(
                selected.get(0),
                ranked,
                selected,
                new CandidateSelectionExplanation(
                        RoutingSelectionMode.PRIMARY_ESCALATION,
                        selected.size() > 1 ? "PRIMARY_AND_ESCALATION" : "PRIMARY_ONLY_NO_ESCALATION",
                        false,
                        null));
    }

    private String tieBreakCriterion(ScoredCandidate winner, ScoredCandidate runnerUp) {
        double winnerPolicy = normalized(winner, CandidateScoreDimension.POLICY_PREFERENCE);
        double runnerPolicy = normalized(runnerUp, CandidateScoreDimension.POLICY_PREFERENCE);
        if (Math.abs(winnerPolicy - runnerPolicy) > SCORE_EPSILON) {
            return "POLICY_PREFERENCE";
        }
        return "ORIGINAL_ORDER";
    }

    /**
     * Applies Pareto pruning and bounded Top-K selection.
     *
     * <p>If Top-K is greater than or equal to the candidate count, no heap is
     * needed and the optimized set is returned directly. Pareto pruning is
     * deliberately disabled for very large candidate sets because its exact
     * pairwise implementation is O(C^2); the bounded Top-K path remains
     * O(C log K).</p>
     */
    public List<ScoredCandidate> optimizeCandidates(List<ScoredCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredCandidate> valid = nonNullCandidates(candidates);
        if (valid.isEmpty()) {
            return List.of();
        }

        List<ScoredCandidate> paretoInput = valid;
        if (paretoEnabled && valid.size() > paretoMaxCandidates) {
            // Deterministic approximation: pre-reduce by the already computed
            // aggregate score before the bounded quadratic Pareto stage. This
            // preserves the hot-path bound but is not an exact global Pareto
            // frontier for candidate sets larger than the configured bound.
            paretoInput = topK(valid, paretoMaxCandidates);
        }

        List<ScoredCandidate> paretoReduced = paretoInput;
        if (paretoEnabled && paretoInput.size() > 1) {
            paretoReduced = paretoFrontier(paretoInput);
        }

        if (topK <= 0 || paretoReduced.size() <= topK) {
            return List.copyOf(paretoReduced);
        }

        return topK(paretoReduced, topK);
    }

    /**
     * Returns candidates in deterministic winner-first order.
     * This method intentionally performs a full sort because it is a public
     * ranking operation; the normal selection path calls optimizeCandidates()
     * first and therefore sorts at most K candidates.
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

        indexed.sort(this::compareIndexed);

        return indexed.stream()
                .map(IndexedCandidate::candidate)
                .toList();
    }

    private List<ScoredCandidate> topK(
            List<ScoredCandidate> candidates,
            int limit) {

        PriorityQueue<IndexedCandidate> worstFirst =
                new PriorityQueue<>(limit, this::compareIndexedWorstFirst);

        for (int i = 0; i < candidates.size(); i++) {
            IndexedCandidate candidate =
                    new IndexedCandidate(candidates.get(i), i);

            if (worstFirst.size() < limit) {
                worstFirst.offer(candidate);
                continue;
            }

            IndexedCandidate worst = worstFirst.peek();
            if (compareIndexed(candidate, worst) < 0) {
                // candidate is better than the current worst retained item.
                worstFirst.poll();
                worstFirst.offer(candidate);
            }
        }

        return worstFirst.stream()
                .sorted(this::compareIndexed)
                .map(IndexedCandidate::candidate)
                .toList();
    }

    private List<ScoredCandidate> paretoFrontier(List<ScoredCandidate> candidates) {
        List<ScoredCandidate> frontier = new ArrayList<>(candidates.size());

        for (int i = 0; i < candidates.size(); i++) {
            ScoredCandidate candidate = candidates.get(i);
            boolean dominated = false;

            for (int j = 0; j < candidates.size(); j++) {
                if (i == j) {
                    continue;
                }

                if (dominates(candidates.get(j), candidate)) {
                    dominated = true;
                    break;
                }
            }

            if (!dominated) {
                frontier.add(candidate);
            }
        }

        return frontier;
    }

    private boolean dominates(ScoredCandidate left, ScoredCandidate right) {
        boolean strictlyBetter = false;

        for (CandidateScoreDimension dimension : CandidateScoreDimension.values()) {
            double leftScore = normalized(left, dimension);
            double rightScore = normalized(right, dimension);

            if (leftScore + SCORE_EPSILON < rightScore) {
                return false;
            }
            if (leftScore > rightScore + SCORE_EPSILON) {
                strictlyBetter = true;
            }
        }

        return strictlyBetter;
    }

    private List<ScoredCandidate> nonNullCandidates(List<ScoredCandidate> candidates) {
        List<ScoredCandidate> result = new ArrayList<>(candidates.size());
        for (ScoredCandidate candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }
        return result;
    }

    private int compareIndexed(IndexedCandidate left, IndexedCandidate right) {
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

        return Integer.compare(left.originalIndex(), right.originalIndex());
    }

    private int compareIndexedWorstFirst(IndexedCandidate left, IndexedCandidate right) {
        return compareIndexed(right, left);
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
