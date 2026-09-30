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
import java.util.LinkedHashMap;

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
    void shouldAdvertiseVisionOnlyForConfiguredOllamaVisionModel() {
        when(providerFactory.getProvider(Provider.OLLAMA))
                .thenReturn(provider);

        when(provider.defaultModel())
                .thenReturn("llama3.2:3b");

        com.ai.gateway.config.OllamaConfig ollamaConfig =
                new com.ai.gateway.config.OllamaConfig();
        ollamaConfig.setModel("llama3.2:3b");
        ollamaConfig.setModels(java.util.List.of(
                "llama3.2:3b",
                "qwen2.5vl:3b"));
        ollamaConfig.setVisionModel("qwen2.5vl:3b");

        registry = new ModelRegistryImpl(
                providerFactory,
                ollamaConfig,
                "VISION,AUDIO,TOOLS,REASONING",
                "VISION,AUDIO,TOOLS,REASONING",
                "",
                "VISION,TOOLS,REASONING");

        assertTrue(registry.find(Provider.OLLAMA, "qwen2.5vl:3b")
                .orElseThrow()
                .supports("VISION"));

        assertTrue(registry.find(Provider.OLLAMA, "llama3.2:3b")
                .orElseThrow()
                .capabilities()
                .stream()
                .noneMatch("VISION"::equals));
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

    @Test
    void shouldExpandModelAcrossEnabledProviderEndpoints() {
        when(providerFactory.getProvider(Provider.OLLAMA))
                .thenReturn(provider);
        when(provider.defaultModel())
                .thenReturn("llama3.2:3b");

        com.ai.gateway.config.OllamaConfig ollamaConfig =
                new com.ai.gateway.config.OllamaConfig();
        ollamaConfig.setModel("llama3.2:3b");

        com.ai.gateway.core.routing.registry.ProviderEndpointProperties properties =
                new com.ai.gateway.core.routing.registry.ProviderEndpointProperties();

        var gpu1 = new com.ai.gateway.core.routing.registry.ProviderEndpointProperties.Endpoint();
        gpu1.setProvider(Provider.OLLAMA);
        gpu1.setUrl("http://gpu-01:11434");

        var gpu2 = new com.ai.gateway.core.routing.registry.ProviderEndpointProperties.Endpoint();
        gpu2.setProvider(Provider.OLLAMA);
        gpu2.setUrl("http://gpu-02:11434");

        var disabled = new com.ai.gateway.core.routing.registry.ProviderEndpointProperties.Endpoint();
        disabled.setProvider(Provider.OLLAMA);
        disabled.setUrl("http://gpu-disabled:11434");
        disabled.setEnabled(false);

        var wrongProvider = new com.ai.gateway.core.routing.registry.ProviderEndpointProperties.Endpoint();
        wrongProvider.setProvider(Provider.GEMINI);
        wrongProvider.setUrl("http://gemini:443");

        properties.setDefinitions(new LinkedHashMap<>(java.util.Map.of(
                "ollama-gpu-01", gpu1,
                "ollama-gpu-02", gpu2,
                "ollama-disabled", disabled,
                "ollama-wrong-provider", wrongProvider)));

        var endpointRegistry =
                new com.ai.gateway.core.routing.registry.ProviderEndpointRegistry(properties);

        registry = new ModelRegistryImpl(
                providerFactory,
                ollamaConfig,
                "VISION,AUDIO,TOOLS,REASONING",
                "VISION,AUDIO,TOOLS,REASONING",
                "",
                "VISION,AUDIO,TOOLS,REASONING",
                "VISION,TOOLS,REASONING",
                "TOOLS,REASONING",
                new com.ai.gateway.core.routing.registry.ModelContextWindowProperties(),
                endpointRegistry);

        var definitions = registry.findAll(Provider.OLLAMA, "llama3.2:3b");

        assertEquals(2, definitions.size());
        assertEquals(
                java.util.Set.of("ollama-gpu-01", "ollama-gpu-02"),
                definitions.stream().map(ModelDefinition::endpointId).collect(java.util.stream.Collectors.toSet()));
    }


}
