package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.config.PricingConfig;
import com.ai.gateway.cost.dto.ModelPricing;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.PolicyBasedRoutingStrategy;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.constraint.CandidateConstraintEvaluator;
import com.ai.gateway.routing.engine.CandidateEligibilityFilter;
import com.ai.gateway.routing.engine.CandidateModelResolver;
import com.ai.gateway.routing.engine.CandidateProviderResolver;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.policy.RoutingPolicyService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import com.ai.gateway.routing.scoring.impl.CandidateScoringEngineImpl;
import com.ai.gateway.routing.scoring.strategy.AvailabilityScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.CostScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.LatencyScoreStrategy;
import com.ai.gateway.routing.scoring.strategy.PolicyPreferenceScoreStrategy;
import com.ai.gateway.routing.selection.impl.CandidateSelectionEngineImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PolicyBasedRoutingScoringIntegrationTest {

    private RoutingPolicyService policyService;
    private ProviderModelRegistryService registryService;
    private CandidateProviderResolver providerResolver;
    private CandidateModelResolver modelResolver;
    private CandidateEligibilityFilter eligibilityFilter;
    private CandidateConstraintEvaluator constraintEvaluator;
    private PricingConfig pricingConfig;

    private PolicyBasedRoutingStrategy strategy;

    private RoutingPolicy policy;
    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {
        policyService = mock(RoutingPolicyService.class);
        registryService = mock(ProviderModelRegistryService.class);
        providerResolver = mock(CandidateProviderResolver.class);
        modelResolver = mock(CandidateModelResolver.class);
        eligibilityFilter = mock(CandidateEligibilityFilter.class);
        constraintEvaluator = mock(CandidateConstraintEvaluator.class);
        pricingConfig = mock(PricingConfig.class);

        RoutingScoringProperties properties =
                new RoutingScoringProperties();

        properties.getWeights().setCost(0.70);
        properties.getWeights().setLatency(0.10);
        properties.getWeights().setAvailability(0.10);
        properties.getWeights().setPolicyPreference(0.10);

        properties.getLatencyMs().put("OPENAI:gpt-a", 100.0);
        properties.getLatencyMs().put("GEMINI:gemini-b", 900.0);
        properties.getAvailability().put("OPENAI:gpt-a", 1.0);
        properties.getAvailability().put("GEMINI:gemini-b", 0.80);

        when(pricingConfig.getPricing(
                any(Provider.class),
                any(String.class)))
                .thenAnswer(invocation -> {
                    Provider provider = invocation.getArgument(0);
                    String model = invocation.getArgument(1);

                    boolean openAi = provider == Provider.OPENAI;

                    return ModelPricing.builder()
                            .provider(provider)
                            .model(model)
                            .inputPricePerMillionTokens(
                                    openAi
                                            ? new BigDecimal("1.00")
                                            : new BigDecimal("10.00"))
                            .outputPricePerMillionTokens(
                                    openAi
                                            ? new BigDecimal("1.00")
                                            : new BigDecimal("10.00"))
                            .build();
                });

        CandidateScoringEngineImpl scoringEngine =
                new CandidateScoringEngineImpl(
                        List.of(
                                new CostScoreStrategy(pricingConfig),
                                new LatencyScoreStrategy(properties),
                                new AvailabilityScoreStrategy(properties),
                                new PolicyPreferenceScoreStrategy()),
                        properties);

        strategy = new PolicyBasedRoutingStrategy(
                policyService,
                registryService,
                providerResolver,
                modelResolver,
                eligibilityFilter,
                constraintEvaluator,
                scoringEngine,
                new CandidateSelectionEngineImpl());

        policy = new RoutingPolicy(
                true,
                List.of(Provider.OPENAI, Provider.GEMINI),
                List.of("gpt-a", "gemini-b"),
                Provider.GEMINI,
                "gemini-b");

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-b")
                        .build();
    }

    @Test
    void scoringCanSelectHigherValueCandidateInsteadOfPreferredPair() {
        ChatRequest request =
                ChatRequest.builder()
                        .prompt("research")
                        .build();

        RoutingCandidate openAi =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate gemini =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        when(policyService.resolve(request, authenticationContext))
                .thenReturn(policy);

        when(providerResolver.resolve(policy))
                .thenReturn(List.of(Provider.OPENAI, Provider.GEMINI));

        when(modelResolver.resolve(Provider.OPENAI, policy))
                .thenReturn(List.of("gpt-a"));

        when(modelResolver.resolve(Provider.GEMINI, policy))
                .thenReturn(List.of("gemini-b"));

        when(eligibilityFilter.filter(
                List.of(openAi, gemini),
                policy))
                .thenReturn(List.of(openAi, gemini));

        when(constraintEvaluator.filter(
                List.of(openAi, gemini),
                policy))
                .thenReturn(List.of(openAi, gemini));

        RoutingDecision decision =
                strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.POLICY_BASED,
                decision.strategy());

        assertEquals(
                Provider.OPENAI,
                decision.provider());

        assertEquals(
                "gpt-a",
                decision.model());

        assertNotNull(decision.metadata());
        assertEquals(2, decision.metadata().candidateCount());
        assertEquals(1, decision.metadata().selectedRank());
        assertEquals(
                "HIGHEST_SCORE",
                decision.metadata().selectionReason());

        assertEquals(
                2,
                decision.metadata().rankedCandidates().size());
    }

    @Test
    void hardConstraintRejectedCandidateNeverReachesScoringSelection() {
        ChatRequest request =
                ChatRequest.builder()
                        .prompt("research")
                        .build();

        RoutingCandidate openAi =
                new RoutingCandidate(Provider.OPENAI, "gpt-a");

        RoutingCandidate gemini =
                new RoutingCandidate(Provider.GEMINI, "gemini-b");

        when(policyService.resolve(request, authenticationContext))
                .thenReturn(policy);

        when(providerResolver.resolve(policy))
                .thenReturn(List.of(Provider.OPENAI, Provider.GEMINI));

        when(modelResolver.resolve(Provider.OPENAI, policy))
                .thenReturn(List.of("gpt-a"));

        when(modelResolver.resolve(Provider.GEMINI, policy))
                .thenReturn(List.of("gemini-b"));

        when(eligibilityFilter.filter(
                List.of(openAi, gemini),
                policy))
                .thenReturn(List.of(openAi, gemini));

        when(constraintEvaluator.filter(
                List.of(openAi, gemini),
                policy))
                .thenReturn(List.of(gemini));

        RoutingDecision decision =
                strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(Provider.GEMINI, decision.provider());
        assertEquals("gemini-b", decision.model());
        assertEquals(1, decision.metadata().candidateCount());
    }
}
