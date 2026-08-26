package com.ai.gateway.routing.registry.impl;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ModelCapabilities;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ModelStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Startup-built immutable model registry.
 *
 * Providers with a single configured default model keep their existing
 * representation. Ollama may expose multiple configured models; all of them
 * become first-class model definitions for routing, explicit model selection,
 * and the model catalog.
 */
@Component
public class ModelRegistryImpl implements ModelRegistry {

    private final Map<Provider, List<ModelDefinition>> modelsByProvider;
    private final Map<String, ModelDefinition> modelsById;

    public ModelRegistryImpl(AIProviderFactory providerFactory) {
        this(
                providerFactory,
                new OllamaConfig(),
                "VISION,AUDIO,TOOLS,REASONING",
                "VISION,AUDIO,TOOLS,REASONING",
                "",
                "VISION,AUDIO,TOOLS,REASONING");
    }

    @Autowired
    public ModelRegistryImpl(
            AIProviderFactory providerFactory,
            OllamaConfig ollamaConfig,
            @Value("${gateway.routing.model-capabilities.openai:VISION,AUDIO,TOOLS,REASONING}") String openAiCapabilities,
            @Value("${gateway.routing.model-capabilities.gemini:VISION,AUDIO,TOOLS,REASONING}") String geminiCapabilities,
            @Value("${gateway.routing.model-capabilities.ollama:}") String ollamaCapabilities,
            @Value("${gateway.routing.model-capabilities.claude:VISION,AUDIO,TOOLS,REASONING}") String claudeCapabilities) {

        EnumMap<Provider, List<ModelDefinition>> byProvider =
                new EnumMap<>(Provider.class);
        HashMap<String, ModelDefinition> byId = new HashMap<>();

        for (Provider provider : Provider.values()) {
            try {
                AIProvider aiProvider = providerFactory.getProvider(provider);
                String defaultModel = aiProvider.defaultModel();

                List<String> configuredModels =
                        modelsFor(provider, defaultModel, ollamaConfig);

                if (configuredModels.isEmpty()) {
                    continue;
                }

                List<ModelDefinition> definitions = new ArrayList<>();

                for (String model : configuredModels) {
                    ModelDefinition definition = new ModelDefinition(
                            provider,
                            model,
                            model,
                            ModelStatus.ENABLED,
                            capabilitiesFor(
                                    provider,
                                    openAiCapabilities,
                                    geminiCapabilities,
                                    ollamaCapabilities,
                                    claudeCapabilities));

                    definitions.add(definition);

                    // Explicit model-only requests need a globally unique
                    // model lookup. Preserve the existing first-registration
                    // behavior if two providers expose the same model ID.
                    byId.putIfAbsent(model, definition);
                }

                byProvider.put(provider, List.copyOf(definitions));
            } catch (Exception ignored) {
                // Provider is unavailable; it simply has no registered models.
            }
        }

        this.modelsByProvider = Map.copyOf(byProvider);
        this.modelsById = Map.copyOf(byId);
    }

    private List<String> modelsFor(
            Provider provider,
            String defaultModel,
            OllamaConfig ollamaConfig) {

        if (provider != Provider.OLLAMA) {
            return defaultModel == null || defaultModel.isBlank()
                    ? List.of()
                    : List.of(defaultModel);
        }

        LinkedHashSet<String> models = new LinkedHashSet<>();

        // Keep the configured default first so defaultModel(provider) remains
        // backward compatible with the existing single-model behavior.
        if (defaultModel != null && !defaultModel.isBlank()) {
            models.add(defaultModel.trim());
        }

        if (ollamaConfig != null && ollamaConfig.getModels() != null) {
            ollamaConfig.getModels().stream()
                    .filter(model -> model != null && !model.isBlank())
                    .map(String::trim)
                    .forEach(models::add);
        }

        return List.copyOf(models);
    }

    private Set<String> capabilitiesFor(
            Provider provider,
            String openAi,
            String gemini,
            String ollama,
            String claude) {

        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(ModelCapabilities.CHAT);

        String raw = switch (provider) {
            case OPENAI -> openAi;
            case GEMINI -> gemini;
            case OLLAMA -> ollama;
            case CLAUDE -> claude;
        };

        if (raw != null) {
            for (String capability : raw.split(",")) {
                if (!capability.isBlank()) {
                    result.add(capability.trim().toUpperCase());
                }
            }
        }

        return Set.copyOf(result);
    }

    @Override
    public Optional<ModelDefinition> find(
            Provider provider,
            String modelId) {

        if (provider == null || modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }

        return modelsByProvider
                .getOrDefault(provider, List.of())
                .stream()
                .filter(definition -> modelId.equals(definition.modelId()))
                .findFirst();
    }

    @Override
    public Optional<ModelDefinition> findByModel(String modelId) {
        return modelId == null || modelId.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(modelsById.get(modelId));
    }

    @Override
    public List<ModelDefinition> findAll() {
        return List.copyOf(modelsById.values());
    }

    @Override
    public List<ModelDefinition> findByProvider(Provider provider) {
        return provider == null
                ? List.of()
                : modelsByProvider.getOrDefault(provider, List.of());
    }

    @Override
    public boolean exists(
            Provider provider,
            String modelId) {
        return find(provider, modelId).isPresent();
    }

    @Override
    public String defaultModel(Provider provider) {
        if (provider == null) {
            throw new BusinessException(
                    "Provider is required to resolve default model.");
        }

        List<ModelDefinition> definitions =
                modelsByProvider.getOrDefault(provider, List.of());

        if (definitions.isEmpty()) {
            throw new BusinessException(
                    "No default model registered for provider "
                            + provider
                            + ".");
        }

        return definitions.get(0).modelId();
    }
}
