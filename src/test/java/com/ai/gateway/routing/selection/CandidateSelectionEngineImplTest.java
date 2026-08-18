package com.ai.gateway.routing.selection;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@ActiveProfiles("test")
class CandidateSelectionEngineImplTest {

    private final CandidateSelectionEngineImpl engine =
            new CandidateSelectionEngineImpl();

    @Test
    void selectsHighestAggregateScore() {
        ScoredCandidate lower =
                scored(Provider.OPENAI, "gpt-a", 0.40, 0.0);
        ScoredCandidate higher =
                scored(Provider.GEMINI, "gemini-b", 0.80, 1.0);

        assertEquals(
                higher.candidate(),
                engine.select(List.of(lower, higher)).candidate());
    }

    @Test
    void policyPreferenceBreaksAggregateScoreTie() {
        ScoredCandidate nonPreferred =
                scored(Provider.OPENAI, "gpt-a", 0.70, 0.0);
        ScoredCandidate preferred =
                scored(Provider.GEMINI, "gemini-b", 0.70, 1.0);

        assertEquals(
                preferred.candidate(),
                engine.select(List.of(nonPreferred, preferred)).candidate());
    }

    @Test
    void originalOrderBreaksCompleteTieDeterministically() {
        ScoredCandidate first =
                scored(Provider.OPENAI, "gpt-a", 0.70, 0.5);
        ScoredCandidate second =
                scored(Provider.GEMINI, "gemini-b", 0.70, 0.5);

        assertEquals(
                first.candidate(),
                engine.select(List.of(first, second)).candidate());

        assertEquals(
                List.of(first, second),
                engine.rank(List.of(first, second)));
    }


    @Test
    void boundedTopKRetainsOnlyBestCandidates() {
        List<ScoredCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            candidates.add(
                    scored(
                            i % 2 == 0 ? Provider.OPENAI : Provider.GEMINI,
                            "model-" + i,
                            i / 10.0,
                            0.0));
        }

        List<ScoredCandidate> optimized = engine.optimizeCandidates(candidates);

        assertEquals(5, optimized.size());
        assertEquals(0.9, optimized.get(0).totalScore(), 1.0e-9);
        assertEquals(0.5, optimized.get(4).totalScore(), 1.0e-9);
    }

    @Test
    void paretoDominatedCandidateIsRemovedBeforeTopK() {
        ScoredCandidate dominated = scoredWithDimensions(
                Provider.OPENAI,
                "dominated",
                0.40,
                0.20,
                0.20,
                0.20,
                0.20);

        ScoredCandidate dominator = scoredWithDimensions(
                Provider.GEMINI,
                "dominator",
                0.80,
                0.80,
                0.80,
                0.80,
                0.80);

        List<ScoredCandidate> optimized =
                engine.optimizeCandidates(List.of(dominated, dominator));

        assertEquals(1, optimized.size());
        assertEquals(dominator.candidate(), optimized.get(0).candidate());
    }

    @Test
    void selectionUsesOptimizedCandidateSetWithoutChangingWinner() {
        List<ScoredCandidate> candidates = List.of(
                scored(Provider.OPENAI, "gpt-a", 0.10, 0.0),
                scored(Provider.GEMINI, "gemini-b", 0.90, 0.0),
                scored(Provider.OPENAI, "gpt-c", 0.50, 0.0),
                scored(Provider.GEMINI, "gemini-d", 0.70, 0.0),
                scored(Provider.OPENAI, "gpt-e", 0.30, 0.0),
                scored(Provider.GEMINI, "gemini-f", 0.60, 0.0));

        assertEquals(
                Provider.GEMINI,
                engine.select(candidates).candidate().provider());
        assertEquals(
                "gemini-b",
                engine.select(candidates).candidate().model());
    }

    @Test
    void rejectsEmptySelectionInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.select(List.of()));
    }

    private ScoredCandidate scoredWithDimensions(
            Provider provider,
            String model,
            double total,
            double cost,
            double latency,
            double availability,
            double policyPreference) {

        return new ScoredCandidate(
                new RoutingCandidate(provider, model),
                List.of(
                        new CandidateScoreComponent(
                                CandidateScoreDimension.COST,
                                cost, cost, 0.25, cost * 0.25),
                        new CandidateScoreComponent(
                                CandidateScoreDimension.LATENCY,
                                latency, latency, 0.25, latency * 0.25),
                        new CandidateScoreComponent(
                                CandidateScoreDimension.AVAILABILITY,
                                availability, availability, 0.25, availability * 0.25),
                        new CandidateScoreComponent(
                                CandidateScoreDimension.POLICY_PREFERENCE,
                                policyPreference, policyPreference, 0.25, policyPreference * 0.25)),
                total);
    }

    private ScoredCandidate scored(
            Provider provider,
            String model,
            double total,
            double policyPreference) {

        return new ScoredCandidate(
                new RoutingCandidate(provider, model),
                List.of(
                        new CandidateScoreComponent(
                                CandidateScoreDimension.POLICY_PREFERENCE,
                                policyPreference,
                                policyPreference,
                                1.0,
                                policyPreference)),
                total);
    }
}
