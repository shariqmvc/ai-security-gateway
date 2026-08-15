package com.ai.gateway.service;

import com.ai.gateway.cost.config.PricingConfig;
import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.routing.scoring.ScoredCandidate;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import com.ai.gateway.routing.scoring.impl.CandidateScoringEngineImpl;
import com.ai.gateway.routing.scoring.strategy.AvailabilityScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.CostScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.LatencyScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.PolicyPreferenceScoreStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateScoringEngineImplTest {

    @Mock
    private PricingConfig pricingConfig;

    private RoutingScoringProperties properties;
    private CandidateScoringEngineImpl engine;

    @BeforeEach
    void setUp() {
        properties = new RoutingScoringProperties();

        properties.getWeights().setCost(0.30);
        properties.getWeights().setLatency(0.25);
        properties.getWeights().setAvailability(0.20);
        properties.getWeights().setPolicyPreference(0.25);

        properties.getLatencyMs().put("OPENAI:gpt-a", 200.0);
        properties.getLatencyMs().put("GEMINI:gemini-b", 800.0);

        properties.getAvailability().put("OPENAI:gpt-a", 0.99);
        properties.getAvailability().put("GEMINI:gemini-b", 0.80);

        engine = new CandidateScoringEngineImpl(
                List.of(
                        new CostScoreStrategy(pricingConfig),
                        new LatencyScoreStrategy(properties),
                        new AvailabilityScoreStrategy(properties),
                        new PolicyPreferenceScoreStrategy()
                ),
                properties
        );
    }

    @Test
    void scoresAllConfiguredDimensions() {
        stubPricing();

        RoutingCandidate openAi =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate gemini =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        RoutingPolicy policy = policy();

        List<ScoredCandidate> result = engine.score(
                List.of(openAi, gemini),
                CandidateScoringContext.standard(policy)
        );

        assertEquals(2, result.size());

        assertEquals(
                4,
                result.get(0).components().size()
        );

        assertEquals(
                4,
                result.get(1).components().size()
        );

        assertTrue(
                result.stream().allMatch(candidate ->
                        candidate.totalScore() >= 0.0
                                && candidate.totalScore() <= 1.0
                )
        );
    }

    @Test
    void lowerCostAndLatencyProduceHigherNormalizedScores() {
        stubPricing();

        RoutingCandidate openAi =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate gemini =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        List<ScoredCandidate> result = engine.score(
                List.of(openAi, gemini),
                CandidateScoringContext.standard(policy())
        );

        ScoredCandidate openAiScore = result.get(0);
        ScoredCandidate geminiScore = result.get(1);

        assertEquals(
                1.0,
                component(
                        openAiScore,
                        CandidateScoreDimension.COST
                ).normalizedScore()
        );

        assertEquals(
                0.0,
                component(
                        geminiScore,
                        CandidateScoreDimension.COST
                ).normalizedScore()
        );

        assertEquals(
                1.0,
                component(
                        openAiScore,
                        CandidateScoreDimension.LATENCY
                ).normalizedScore()
        );

        assertEquals(
                0.0,
                component(
                        geminiScore,
                        CandidateScoreDimension.LATENCY
                ).normalizedScore()
        );
    }

    @Test
    void policyPreferenceIsScoredAsSoftPreference() {
        stubPricing();

        RoutingCandidate preferred =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate alternative =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        List<ScoredCandidate> result = engine.score(
                List.of(preferred, alternative),
                CandidateScoringContext.standard(policy())
        );

        assertEquals(
                1.0,
                component(
                        result.get(0),
                        CandidateScoreDimension.POLICY_PREFERENCE
                ).normalizedScore()
        );

        assertEquals(
                0.0,
                component(
                        result.get(1),
                        CandidateScoreDimension.POLICY_PREFERENCE
                ).normalizedScore()
        );
    }

    @Test
    void configuredAvailabilityIsUsedAsScoringSignal() {
        stubPricing();

        RoutingCandidate openAi =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate gemini =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        List<ScoredCandidate> result = engine.score(
                List.of(openAi, gemini),
                CandidateScoringContext.standard(policy())
        );

        assertEquals(
                0.99,
                component(
                        result.get(0),
                        CandidateScoreDimension.AVAILABILITY
                ).rawValue()
        );

        assertEquals(
                0.80,
                component(
                        result.get(1),
                        CandidateScoreDimension.AVAILABILITY
                ).rawValue()
        );
    }

    @Test
    void unityContextIsAcceptedWithoutChangingScoringContract() {
        stubPricing();

        RoutingCandidate candidate =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        CandidateScoringContext context =
                CandidateScoringContext.standard(policy())
                        .forUnityRole("research-synthesis");

        List<ScoredCandidate> result = engine.score(
                List.of(candidate),
                context
        );

        assertEquals(1, result.size());

        assertTrue(
                result.get(0).totalScore() >= 0.0
        );

        assertEquals(
                1.0,
                component(
                        result.get(0),
                        CandidateScoreDimension.COST
                ).normalizedScore()
        );

        assertTrue(context.extensiveResearchEnabled());

        assertEquals(
                "research-synthesis",
                context.executionRole()
        );
    }

    @Test
    void emptyCandidatesReturnEmptyResult() {
        List<ScoredCandidate> result = engine.score(
                List.of(),
                CandidateScoringContext.standard(policy())
        );

        assertTrue(result.isEmpty());
    }

    private void stubPricing() {
        when(pricingConfig.getPricing(
                any(Provider.class),
                any(String.class)
        )).thenAnswer(invocation -> {
            Provider provider =
                    invocation.getArgument(0);

            String model =
                    invocation.getArgument(1);

            BigDecimal input =
                    provider == Provider.OPENAI
                            ? new BigDecimal("1.00")
                            : new BigDecimal("2.00");

            BigDecimal output =
                    provider == Provider.OPENAI
                            ? new BigDecimal("2.00")
                            : new BigDecimal("4.00");

            return ModelPricing.builder()
                    .provider(provider)
                    .model(model)
                    .inputPricePerMillionTokens(input)
                    .outputPricePerMillionTokens(output)
                    .build();
        });
    }

    private RoutingPolicy policy() {
        return new RoutingPolicy(
                true,
                List.of(
                        Provider.OPENAI,
                        Provider.GEMINI
                ),
                List.of(
                        "gpt-a",
                        "gemini-b"
                ),
                Provider.OPENAI,
                "gpt-a"
        );
    }

    private com.ai.gateway.routing.scoring.CandidateScoreComponent component(
            ScoredCandidate candidate,
            CandidateScoreDimension dimension
    ) {
        return candidate.components()
                .stream()
                .filter(component ->
                        component.dimension() == dimension
                )
                .findFirst()
                .orElseThrow();
    }
}