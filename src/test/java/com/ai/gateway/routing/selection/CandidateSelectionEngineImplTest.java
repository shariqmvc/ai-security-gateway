package com.ai.gateway.routing.selection;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreComponent;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

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
    void rejectsEmptySelectionInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.select(List.of()));
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
