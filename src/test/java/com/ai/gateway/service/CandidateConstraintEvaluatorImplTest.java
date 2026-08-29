package com.ai.gateway.service;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.constraint.CandidateConstraintEvaluation;
import com.ai.gateway.core.routing.constraint.CandidateConstraintEvaluatorImpl;
import com.ai.gateway.core.routing.constraint.ConstraintEvaluationResult;
import com.ai.gateway.core.routing.constraint.HardConstraintType;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import com.ai.gateway.core.routing.registry.ModelStatus;
import com.ai.gateway.core.routing.registry.ProviderDefinition;
import com.ai.gateway.core.routing.registry.ProviderRegistry;
import com.ai.gateway.core.routing.registry.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CandidateConstraintEvaluatorImplTest {

    private ProviderRegistry providerRegistry;
    private ModelRegistry modelRegistry;
    private CandidateConstraintEvaluatorImpl evaluator;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(ProviderRegistry.class);
        modelRegistry = mock(ModelRegistry.class);

        evaluator =
                new CandidateConstraintEvaluatorImpl(
                        providerRegistry,
                        modelRegistry);
    }

    @Test
    void shouldPassAllHardConstraintsForEnabledRegisteredCandidate() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        RoutingPolicy policy =
                policyFor(Provider.GEMINI, "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(candidate, policy);

        assertTrue(evaluation.eligible());
        assertTrue(evaluation.failures().isEmpty());
        assertEquals(6, evaluation.results().size());
    }

    @Test
    void shouldRejectCandidateWhenProviderIsDisabled() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.DISABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(
                        candidate,
                        policyFor(Provider.GEMINI, "gemini-test"));

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.PROVIDER_ENABLED);
    }

    @Test
    void shouldRejectCandidateWhenModelIsNotRegistered() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "unknown-model");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "unknown-model"))
                .thenReturn(Optional.empty());

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(
                        candidate,
                        policyFor(Provider.GEMINI, "unknown-model"));

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.MODEL_REGISTERED);
        assertConstraintFailed(
                evaluation,
                HardConstraintType.MODEL_ENABLED);
    }

    @Test
    void shouldRejectCandidateWhenModelIsDisabled() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.DISABLED,
                                Set.of("CHAT"))));

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(
                        candidate,
                        policyFor(Provider.GEMINI, "gemini-test"));

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.MODEL_ENABLED);
    }

    @Test
    void shouldRejectCandidateWhenProviderIsNotAllowedByPolicy() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.OPENAI),
                        List.of(),
                        Provider.OPENAI,
                        "gpt-test");

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(candidate, policy);

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.PROVIDER_ALLOWED_BY_POLICY);
    }

    @Test
    void shouldRejectCandidateWhenModelIsNotAllowedByPolicy() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("other-model"),
                        Provider.GEMINI,
                        "other-model");

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(candidate, policy);

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.MODEL_ALLOWED_BY_POLICY);
    }

    @Test
    void shouldRejectCandidateWhenPolicyIsDisabled() {

        RoutingCandidate candidate =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        CandidateConstraintEvaluation evaluation =
                evaluator.evaluate(candidate, policy);

        assertFalse(evaluation.eligible());
        assertConstraintFailed(
                evaluation,
                HardConstraintType.PROVIDER_ALLOWED_BY_POLICY);
        assertConstraintFailed(
                evaluation,
                HardConstraintType.MODEL_ALLOWED_BY_POLICY);
    }

    @Test
    void filterShouldKeepOnlyCandidatesPassingAllHardConstraints() {

        RoutingCandidate enabled =
                new RoutingCandidate(
                        Provider.GEMINI,
                        "gemini-test");

        RoutingCandidate disabled =
                new RoutingCandidate(
                        Provider.OPENAI,
                        "gpt-test");

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI, Provider.OPENAI),
                        List.of("gemini-test", "gpt-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(providerRegistry.find(Provider.GEMINI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.GEMINI,
                                "Gemini",
                                ProviderStatus.ENABLED,
                                Set.of("CHAT"))));

        when(providerRegistry.find(Provider.OPENAI))
                .thenReturn(Optional.of(
                        new ProviderDefinition(
                                Provider.OPENAI,
                                "OpenAI",
                                ProviderStatus.DISABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(Provider.GEMINI, "gemini-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.GEMINI,
                                "gemini-test",
                                "Gemini Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        when(modelRegistry.find(Provider.OPENAI, "gpt-test"))
                .thenReturn(Optional.of(
                        new ModelDefinition(
                                Provider.OPENAI,
                                "gpt-test",
                                "GPT Test",
                                ModelStatus.ENABLED,
                                Set.of("CHAT"))));

        List<RoutingCandidate> result =
                evaluator.filter(
                        List.of(enabled, disabled),
                        policy);

        assertEquals(List.of(enabled), result);
    }

    private RoutingPolicy policyFor(
            Provider provider,
            String model) {

        return new RoutingPolicy(
                true,
                List.of(provider),
                List.of(model),
                provider,
                model);
    }

    private void assertConstraintFailed(
            CandidateConstraintEvaluation evaluation,
            HardConstraintType type) {

        assertTrue(
                evaluation.results().stream()
                        .anyMatch(result ->
                                result.type() == type
                                        && !result.passed()));
    }
}
