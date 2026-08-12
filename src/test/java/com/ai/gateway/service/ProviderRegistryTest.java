package com.ai.gateway.service;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ProviderDefinition;
import com.ai.gateway.routing.registry.impl.ProviderRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderRegistryTest {

    @Mock
    private AIProviderFactory providerFactory;

    @Mock
    private AIProvider provider;

    private ProviderRegistryImpl registry;

    @BeforeEach
    void setUp() {

        registry =
                new ProviderRegistryImpl(
                        providerFactory);
    }

    @Test
    void shouldReturnEnabledProvider() {

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        ProviderDefinition result =
                registry.find(
                                Provider.GEMINI)
                        .orElseThrow();

        assertEquals(
                Provider.GEMINI,
                result.provider());

        assertTrue(
                result.isEnabled());

        assertTrue(
                result.supports("CHAT"));
    }
}
