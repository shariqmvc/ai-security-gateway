package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyBasedRoutingStrategyTest {

    @Mock
    private RoutingPolicyService routingPolicyService;

    @Mock
    private ProviderModelRegistryService providerModelRegistryService;

    @Mock
    private CandidateProviderResolver candidateProviderResolver;

    @Mock
    private CandidateModelResolver candidateModelResolver;

    @Mock
    private CandidateEligibilityFilter candidateEligibilityFilter;

    @Mock
    private CandidateConstraintEvaluator candidateConstraintEvaluator;

    private PolicyBasedRoutingStrategy strategy;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        strategy =
                new PolicyBasedRoutingStrategy(
                        routingPolicyService,
                        providerModelRegistryService,
                        candidateProviderResolver,
                        candidateModelResolver,
                        candidateEligibilityFilter,
                        candidateConstraintEvaluator);

        lenient().when(candidateConstraintEvaluator.filter(
                anyList(),
                any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();
    }

    @Test
    void shouldSupportRequestWithoutExplicitProviderOrModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertTrue(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportExplicitProviderRequest() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportExplicitModelRequest() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportNullContext() {

        assertFalse(
                strategy.supports(null));
    }

    @Test
    void shouldNotSupportNullRequest() {

        RoutingContext context =
                new RoutingContext(
                        null,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportNullAuthenticationContext() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        null);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldRouteUsingPolicyPreferredProviderAndModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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

        RoutingCandidate candidate =
                stubCandidate(
                        policy,
                        Provider.GEMINI,
                        "gemini-test");

        RoutingDecision decision =
                strategy.route(context);

        assertNotNull(decision);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.POLICY_BASED,
                decision.strategy());

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");
    }

    @Test
    void shouldRejectNullPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldRejectDisabledPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldRejectPolicyWithoutProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldRejectPolicyWithoutModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        Provider.GEMINI,
                        null);

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldRejectPreferredProviderNotAllowedByPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.OPENAI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldRejectPreferredModelNotAllowedByPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("other-model"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService,
                candidateProviderResolver,
                candidateModelResolver,
                candidateEligibilityFilter,
                candidateConstraintEvaluator);
    }

    @Test
    void shouldPropagateProviderRegistryFailure() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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

        stubCandidate(
                policy,
                Provider.GEMINI,
                "gemini-test");

        doThrow(
                new BusinessException(
                        "GEMINI provider is not available."))
                .when(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(
                providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        anyString());
    }

    @Test
    void shouldPropagateModelRegistryFailure() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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

        stubCandidate(
                policy,
                Provider.GEMINI,
                "gemini-test");

        doThrow(
                new BusinessException(
                        "Model gemini-test is not available."))
                .when(providerModelRegistryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");
    }

    @Test
    void shouldSelectPreferredProviderWhenEligible() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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
                any(),
                any()))
                .thenReturn(policy);

        RoutingCandidate openAiCandidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        RoutingCandidate geminiCandidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI));

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
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.POLICY_BASED,
                decision.strategy());
    }

    @Test
    void shouldSelectFirstCandidateWhenPreferredProviderIsNotEligible() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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
                any(),
                any()))
                .thenReturn(policy);

        RoutingCandidate openAiCandidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(
                        List.of(Provider.OPENAI));

        when(candidateModelResolver.resolve(
                Provider.OPENAI,
                policy))
                .thenReturn(
                        List.of("gpt-test"));

        when(candidateEligibilityFilter.filter(
                List.of(openAiCandidate),
                policy))
                .thenReturn(
                        List.of(openAiCandidate));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.OPENAI,
                decision.provider());

        assertEquals(
                "gpt-test",
                decision.model());
    }

    @Test
    void shouldFailWhenNoEligibleProviderExists() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                any(),
                any()))
                .thenReturn(policy);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(List.of());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> strategy.route(context));

        assertEquals(
                "No eligible provider is available for routing.",
                exception.getMessage());

        verifyNoInteractions(
                candidateModelResolver,
                candidateEligibilityFilter,
                providerModelRegistryService);
    }

    @Test
    void shouldFailWhenNoEligibleModelExists() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                any(),
                any()))
                .thenReturn(policy);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(List.of(Provider.GEMINI));

        when(candidateModelResolver.resolve(
                Provider.GEMINI,
                policy))
                .thenReturn(List.of());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> strategy.route(context));

        assertEquals(
                "No eligible model is available for routing.",
                exception.getMessage());

        verifyNoInteractions(
                candidateEligibilityFilter,
                providerModelRegistryService);
    }

    @Test
    void shouldFailWhenEligibilityFilterRemovesAllCandidates() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                any(),
                any()))
                .thenReturn(policy);

        RoutingCandidate candidate =
                stubCandidate(
                        policy,
                        Provider.GEMINI,
                        "gemini-test");

        when(candidateEligibilityFilter.filter(
                List.of(candidate),
                policy))
                .thenReturn(List.of());

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> strategy.route(context));

        assertEquals(
                "No eligible routing candidate is available.",
                exception.getMessage());

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldNotSelectPreferredProviderOutsideCandidateSet() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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
                any(),
                any()))
                .thenReturn(policy);

        RoutingCandidate openAiCandidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(List.of(Provider.OPENAI));

        when(candidateModelResolver.resolve(
                Provider.OPENAI,
                policy))
                .thenReturn(List.of("gpt-test"));

        when(candidateEligibilityFilter.filter(
                List.of(openAiCandidate),
                policy))
                .thenReturn(List.of(openAiCandidate));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.OPENAI,
                decision.provider());

        assertEquals(
                "gpt-test",
                decision.model());
    }

    @Test
    void shouldNeverPairModelWithWrongProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

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

        RoutingCandidate openAiCandidate =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        RoutingCandidate geminiCandidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

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
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertNotEquals(
                "gpt-test",
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

    private RoutingCandidate stubCandidate(
            RoutingPolicy policy,
            Provider provider,
            String model) {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        provider,
                        model);

        when(candidateProviderResolver.resolve(policy))
                .thenReturn(List.of(provider));

        when(candidateModelResolver.resolve(
                provider,
                policy))
                .thenReturn(List.of(model));

        when(candidateEligibilityFilter.filter(
                List.of(candidate),
                policy))
                .thenReturn(List.of(candidate));

        return candidate;
    }
}