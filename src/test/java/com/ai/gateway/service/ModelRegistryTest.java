package com.ai.gateway.service;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIProviderFactory;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.impl.ModelRegistryImpl;
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
        // Registry is startup-built; each test constructs it after stubbing provider metadata.
    }

    @Test
    void shouldFindDefaultModel() {

        when(providerFactory.getProvider(
                Provider.GEMINI))
                .thenReturn(provider);

        when(provider.defaultModel())
                .thenReturn("gemini-test");

        registry = new ModelRegistryImpl(providerFactory);

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

        registry = new ModelRegistryImpl(providerFactory);

        assertTrue(
                registry.find(
                                Provider.GEMINI,
                                "unknown-model")
                        .isEmpty());
    }

    @Test
    void shouldRegisterAllConfiguredOllamaModels() {
        when(providerFactory.getProvider(Provider.OLLAMA))
                .thenReturn(provider);

        when(provider.defaultModel())
                .thenReturn("llama3.1:8b");

        com.ai.gateway.config.OllamaConfig ollamaConfig =
                new com.ai.gateway.config.OllamaConfig();
        ollamaConfig.setModel("llama3.1:8b");
        ollamaConfig.setModels(java.util.List.of(
                "llama3.1:8b",
                "llama3.2:3b"));

        registry = new ModelRegistryImpl(
                providerFactory,
                ollamaConfig,
                "VISION,AUDIO,TOOLS,REASONING",
                "VISION,AUDIO,TOOLS,REASONING",
                "",
                "VISION,AUDIO,TOOLS,REASONING");

        assertEquals(
                java.util.List.of("llama3.1:8b", "llama3.2:3b"),
                registry.findByProvider(Provider.OLLAMA)
                        .stream()
                        .map(ModelDefinition::modelId)
                        .toList());

        assertTrue(
                registry.find(
                                Provider.OLLAMA,
                                "llama3.2:3b")
                        .isPresent());

        assertEquals(
                "llama3.1:8b",
                registry.defaultModel(Provider.OLLAMA));
    }

}
