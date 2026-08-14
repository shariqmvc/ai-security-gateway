package com.ai.gateway.service;


import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.CandidateModelResolver;
import com.ai.gateway.routing.engine.CandidateModelResolverImpl;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ModelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateModelResolverImplTest {

    @Mock
    private ModelRegistry modelRegistry;

    private CandidateModelResolver resolver;

    @BeforeEach
    void setUp() {
        resolver =
                new CandidateModelResolverImpl(
                        modelRegistry);
    }

    @Test
    void shouldReturnEnabledModelsAllowedByPolicy() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        enabled(
                                Provider.GEMINI,
                                "gemini-pro"),
                        enabled(
                                Provider.GEMINI,
                                "gemini-flash")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(
                                "gemini-test",
                                "gemini-flash"),
                        Provider.GEMINI,
                        "gemini-test");

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of(
                        "gemini-test",
                        "gemini-flash"),
                result);
    }

    @Test
    void shouldExcludeDisabledModels() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        disabled(
                                Provider.GEMINI,
                                "gemini-disabled")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(
                                "gemini-test",
                                "gemini-disabled"),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of("gemini-test"),
                result);
    }

    @Test
    void shouldAllowAllModelsWhenPolicyModelListIsEmpty() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        enabled(
                                Provider.GEMINI,
                                "gemini-pro")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of(
                        "gemini-test",
                        "gemini-pro"),
                result);
    }

    @Test
    void shouldReturnEmptyWhenProviderIsNull() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        null,
                        policy);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsNull() {

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsDisabled() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRegistryReturnsNoModels() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of());

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldIgnoreNullModelDefinitions() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(Arrays.asList(
                        null,
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        null
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of("gemini-test"),
                result);
    }

    @Test
    void shouldIgnoreBlankModelIds() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        enabled(
                                Provider.GEMINI,
                                " "),
                        enabled(
                                Provider.GEMINI,
                                "   ")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of("gemini-test"),
                result);
    }

    @Test
    void shouldIgnoreModelBelongingToDifferentProvider() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        enabled(
                                Provider.OPENAI,
                                "gpt-test")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.GEMINI,
                                Provider.OPENAI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of("gemini-test"),
                result);
    }

    @Test
    void shouldRemoveDuplicateModelIds() {

        when(modelRegistry.findByProvider(
                Provider.GEMINI))
                .thenReturn(List.of(
                        enabled(
                                Provider.GEMINI,
                                "gemini-test"),
                        enabled(
                                Provider.GEMINI,
                                "gemini-test")
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of(),
                        null,
                        null);

        List<String> result =
                resolver.resolve(
                        Provider.GEMINI,
                        policy);

        assertEquals(
                List.of("gemini-test"),
                result);
    }

    private ModelDefinition enabled(
            Provider provider,
            String model) {

        return new ModelDefinition(
                provider,
                model,
                model,
                ModelStatus.ENABLED,
                Set.of("CHAT"));
    }

    private ModelDefinition disabled(
            Provider provider,
            String model) {

        return new ModelDefinition(
                provider,
                model,
                model,
                ModelStatus.DISABLED,
                Set.of("CHAT"));
    }
}
