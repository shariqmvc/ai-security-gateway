package com.ai.gateway.service;


import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.CandidateProviderResolver;
import com.ai.gateway.core.routing.engine.CandidateProviderResolverImpl;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.registry.ProviderDefinition;
import com.ai.gateway.core.routing.registry.ProviderRegistry;
import com.ai.gateway.core.routing.registry.ProviderStatus;
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
class CandidateProviderResolverImplTest {

    @Mock
    private ProviderRegistry providerRegistry;

    private CandidateProviderResolver resolver;

    @BeforeEach
    void setUp() {
        resolver =
                new CandidateProviderResolverImpl(
                        providerRegistry);
    }

    @Test
    void shouldReturnEnabledProvidersAllowedByPolicy() {

        when(providerRegistry.findAll())
                .thenReturn(List.of(
                        enabled(Provider.OPENAI),
                        enabled(Provider.GEMINI),
                        enabled(Provider.CLAUDE)
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI
                        ),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(
                        Provider.OPENAI,
                        Provider.GEMINI
                ),
                result);
    }

    @Test
    void shouldExcludeDisabledProviders() {

        when(providerRegistry.findAll())
                .thenReturn(List.of(
                        enabled(Provider.OPENAI),
                        disabled(Provider.GEMINI),
                        enabled(Provider.CLAUDE)
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI,
                                Provider.CLAUDE
                        ),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(
                        Provider.OPENAI,
                        Provider.CLAUDE
                ),
                result);
    }

    @Test
    void shouldAllowAllEnabledProvidersWhenPolicyProviderListIsEmpty() {

        when(providerRegistry.findAll())
                .thenReturn(List.of(
                        enabled(Provider.OPENAI),
                        enabled(Provider.GEMINI),
                        enabled(Provider.CLAUDE)
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(
                        Provider.OPENAI,
                        Provider.GEMINI,
                        Provider.CLAUDE
                ),
                result);
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsNull() {

        List<Provider> result =
                resolver.resolve(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenPolicyIsDisabled() {

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI
                        ),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenRegistryHasNoProviders() {

        when(providerRegistry.findAll())
                .thenReturn(List.of());

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPreserveRegistryOrder() {

        when(providerRegistry.findAll())
                .thenReturn(List.of(
                        enabled(Provider.CLAUDE),
                        enabled(Provider.GEMINI),
                        enabled(Provider.OPENAI)
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(
                        Provider.CLAUDE,
                        Provider.GEMINI,
                        Provider.OPENAI
                ),
                result);
    }

    @Test
    void shouldIgnoreNullProviderDefinitions() {

        when(providerRegistry.findAll())
                .thenReturn(Arrays.asList(
                        null,
                        enabled(Provider.OPENAI),
                        null
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(Provider.OPENAI),
                result);
    }

    @Test
    void shouldNotUsePreferredProviderToExcludeOtherCandidates() {

        when(providerRegistry.findAll())
                .thenReturn(List.of(
                        enabled(Provider.OPENAI),
                        enabled(Provider.GEMINI)
                ));

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(
                                Provider.OPENAI,
                                Provider.GEMINI
                        ),
                        List.of(),
                        Provider.GEMINI,
                        null);

        List<Provider> result =
                resolver.resolve(policy);

        assertEquals(
                List.of(
                        Provider.OPENAI,
                        Provider.GEMINI
                ),
                result);
    }

    private ProviderDefinition enabled(
            Provider provider) {

        return new ProviderDefinition(
                provider,
                provider.name(),
                ProviderStatus.ENABLED,
                Set.of()
        );
    }

    private ProviderDefinition disabled(
            Provider provider) {

        return new ProviderDefinition(
                provider,
                provider.name(),
                ProviderStatus.DISABLED,
                Set.of()
        );
    }
}