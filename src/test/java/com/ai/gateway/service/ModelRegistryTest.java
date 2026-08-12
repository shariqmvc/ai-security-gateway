package com.ai.gateway.service;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.impl.ModelRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelRegistryTest {

    @Mock
    private AIProviderFactory providerFactory;

    @Mock
    private AIProvider provider;

    private ModelRegistryImpl registry;

    @BeforeEach
    void setUp() {

        registry =
                new ModelRegistryImpl(
                        providerFactory);
    }

    @Test
    void shouldFindDefaultModel() {

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        when(provider.defaultModel())
                .thenReturn("gemini-test");

        ModelDefinition result =
                registry.find(
                                Provider.GEMINI,
                                "gemini-test")
                        .orElseThrow();

        assertEquals(
                Provider.GEMINI,
                result.provider());

        assertEquals(
                "gemini-test",
                result.modelId());

        assertTrue(
                result.isEnabled());
    }

    @Test
    void shouldRejectUnknownModel() {

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        when(provider.defaultModel())
                .thenReturn("gemini-test");

        assertTrue(
                registry.find(
                                Provider.GEMINI,
                                "unknown-model")
                        .isEmpty());
    }
}
