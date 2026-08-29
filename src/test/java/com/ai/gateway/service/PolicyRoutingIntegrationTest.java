package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.ExplicitModelRoutingStrategy;
import com.ai.gateway.core.routing.ExplicitProviderRoutingStrategy;
import com.ai.gateway.core.routing.PolicyBasedRoutingStrategy;
import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.RoutingService;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.routing.TenantDefaultRoutingStrategy;
import com.ai.gateway.core.routing.constraint.CandidateConstraintEvaluator;
import com.ai.gateway.core.routing.engine.CandidateEligibilityFilter;
import com.ai.gateway.core.routing.engine.CandidateModelResolver;
import com.ai.gateway.core.routing.engine.CandidateProviderResolver;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.policy.RoutingPolicyService;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import com.ai.gateway.core.routing.registry.ModelStatus;
import com.ai.gateway.core.routing.registry.ProviderModelRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class PolicyRoutingIntegrationTest {

    private ProviderModelRegistryService registryService;

    private ModelRegistry modelRegistry;

    private RoutingPolicyService routingPolicyService;

    private CandidateProviderResolver candidateProviderResolver;

    private CandidateModelResolver candidateModelResolver;

    private CandidateEligibilityFilter candidateEligibilityFilter;

    private CandidateConstraintEvaluator candidateConstraintEvaluator;

    private RoutingService routingService;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        registryService =
                mock(ProviderModelRegistryService.class);

        modelRegistry =
                mock(ModelRegistry.class);

        routingPolicyService =
                mock(RoutingPolicyService.class);

        candidateProviderResolver =
                mock(CandidateProviderResolver.class);

        candidateModelResolver =
                mock(CandidateModelResolver.class);

        candidateEligibilityFilter =
                mock(CandidateEligibilityFilter.class);

        candidateConstraintEvaluator =
                mock(CandidateConstraintEvaluator.class);

        when(candidateConstraintEvaluator.filter(anyList(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExplicitProviderRoutingStrategy
                explicitProvider =
                new ExplicitProviderRoutingStrategy(
                        registryService,
                        modelRegistry);

        ExplicitModelRoutingStrategy
                explicitModel =
                new ExplicitModelRoutingStrategy(
                        modelRegistry,
                        registryService);

        PolicyBasedRoutingStrategy
                policyBased =
                new PolicyBasedRoutingStrategy(
                        routingPolicyService,
                        registryService,
                        candidateProviderResolver,
                        candidateModelResolver,
                        candidateEligibilityFilter,
                        candidateConstraintEvaluator);

        TenantDefaultRoutingStrategy
                tenantDefault =
                new TenantDefaultRoutingStrategy(
                        registryService);

        routingService =
                new RoutingService(
                        List.of(
                                explicitProvider,
                                explicitModel,
                                policyBased,
                                tenantDefault));

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();
    }

    @Test
    void shouldPreserveExplicitProviderPrecedence() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertNotNull(decision);

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        verifyNoInteractions(
                routingPolicyService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter);
    }

    @Test
    void shouldPreserveExplicitModelPrecedence() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        when(modelRegistry.findByModel("gemini-test"))
                .thenReturn(
                        Optional.of(
                                new ModelDefinition(
                                        Provider.GEMINI,
                                        "gemini-test",
                                        "gemini-test",
                                        ModelStatus.ENABLED,
                                        Set.of("CHAT"))));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertNotNull(decision);

        assertEquals(
                RoutingStrategy.EXPLICIT_MODEL,
                decision.strategy());

        verifyNoInteractions(
                routingPolicyService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter);
    }

    @Test
    void shouldUsePolicyRoutingWhenNoExplicitSelectionExists() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(
                        List.of(Provider.GEMINI));

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(candidateModelResolver.resolve(
                Provider.GEMINI,
                policy))
                .thenReturn(
                        List.of("gemini-test"));

        when(candidateEligibilityFilter.filter(
                List.of(candidate),
                policy))
                .thenReturn(
                        List.of(candidate));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertNotNull(decision);

        assertEquals(
                RoutingStrategy.POLICY_BASED,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        verify(candidateProviderResolver)
                .resolve(policy);

        verify(candidateModelResolver)
                .resolve(
                        Provider.GEMINI,
                        policy);

        verify(candidateEligibilityFilter)
                .filter(
                        List.of(candidate),
                        policy);
    }

    @Test
    void shouldRouteUsingFirstEligibleCandidateWhenPreferredPairIsUnavailable() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI),
                        List.of(
                                "gpt-test",
                                "gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(
                        List.of(Provider.OPENAI));

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        when(candidateModelResolver.resolve(
                Provider.OPENAI,
                policy))
                .thenReturn(
                        List.of("gpt-test"));

        when(candidateEligibilityFilter.filter(
                List.of(candidate),
                policy))
                .thenReturn(
                        List.of(candidate));

        RoutingDecision decision =
                routingService.route(
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
                "gpt-test",
                decision.model());
    }

    @Test
    void shouldNotCreateWrongProviderModelPair() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI),
                        List.of(
                                "gpt-test",
                                "gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI));

        RoutingCandidate openAiCandidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        RoutingCandidate geminiCandidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(candidateModelResolver.resolve(
                Provider.OPENAI,
                policy))
                .thenReturn(
                        List.of("gpt-test"));

        when(candidateModelResolver.resolve(
                Provider.GEMINI,
                policy))
                .thenReturn(
                        List.of("gemini-test"));

        when(candidateEligibilityFilter.filter(
                List.of(
                        openAiCandidate,
                        geminiCandidate),
                policy))
                .thenReturn(
                        List.of(
                                openAiCandidate,
                                geminiCandidate));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        verify(candidateModelResolver)
                .resolve(
                        Provider.OPENAI,
                        policy);

        verify(candidateModelResolver)
                .resolve(
                        Provider.GEMINI,
                        policy);
    }

    /*
     * This test is intentionally left as a documentation boundary.
     *
     * PolicyBasedRoutingStrategy currently supports requests without
     * explicit provider/model selection, therefore such a request reaches
     * policy routing before TenantDefaultRoutingStrategy.
     *
     * Tenant-default fallback should be tested once policy resolution
     * explicitly supports a disabled/unavailable policy fallback path.
     */
    @Test
    void shouldDocumentTenantDefaultPrecedenceBoundary() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        assertNotNull(request);
    }
}