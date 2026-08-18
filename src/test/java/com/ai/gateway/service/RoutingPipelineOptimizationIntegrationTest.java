package com.ai.gateway.service;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.selection.CandidateSelectionResult;
import com.ai.gateway.routing.selection.RoutingSelectionRequest;
import com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingPipelineOptimizationIntegrationTest {

    @Test
    void boundedTopNSelectionHandlesLargeCandidateSetWithoutExpandingResult() {
        List<ScoredCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            double score = i / 1_000.0;
            RoutingCandidate candidate = new RoutingCandidate(
                    Provider.values()[i % Provider.values().length],
                    "model-" + i);
            candidates.add(new ScoredCandidate(
                    candidate,
                    List.of(new CandidateScoreComponent(
                            CandidateScoreDimension.POLICY_PREFERENCE,
                            score,
                            score,
                            1.0,
                            score)),
                    score));
        }

        CandidateSelectionEngineImpl engine =
                new CandidateSelectionEngineImpl(5, false, 64);

        CandidateSelectionResult result = engine.select(
                candidates,
                RoutingSelectionRequest.topN(3));

        assertEquals(3, result.selectedCandidates().size());
        assertEquals(5, result.rankedCandidates().size());
        assertEquals(0.999, result.selected().totalScore(), 1.0e-12);
        assertEquals("TOP_N_UTILITY", result.explanation().decisionReason());
    }
}
