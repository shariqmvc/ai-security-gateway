package com.ai.gateway.routing.registry.impl;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ModelStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Startup-built immutable model registry. The current MVP exposes one default
 * model per provider; keep that representation in memory so routing does not
 * recreate ModelDefinition instances on every request.
 */
@Component
public class ModelRegistryImpl implements ModelRegistry {

    private final Map<Provider, ModelDefinition> modelsByProvider;
    private final Map<String, ModelDefinition> modelsById;

    public ModelRegistryImpl(AIProviderFactory providerFactory) {
        EnumMap<Provider, ModelDefinition> byProvider = new EnumMap<>(Provider.class);
        java.util.HashMap<String, ModelDefinition> byId = new java.util.HashMap<>();

        for (Provider provider : Provider.values()) {
            try {
                AIProvider aiProvider = providerFactory.getProvider(provider);
                String model = aiProvider.defaultModel();
                if (model != null && !model.isBlank()) {
                    ModelDefinition definition = new ModelDefinition(
                            provider, model, model, ModelStatus.ENABLED, Set.of("CHAT"));
                    byProvider.put(provider, definition);
                    byId.putIfAbsent(model, definition);
                }
            } catch (Exception ignored) {
                // Provider is unavailable; it simply has no registered model.
            }
        }

        this.modelsByProvider = Map.copyOf(byProvider);
        this.modelsById = Map.copyOf(byId);
    }

    @Override
    public Optional<ModelDefinition> find(Provider provider, String modelId) {
        if (provider == null || modelId == null || modelId.isBlank()) return Optional.empty();
        ModelDefinition definition = modelsByProvider.get(provider);
        return definition != null && modelId.equals(definition.modelId())
                ? Optional.of(definition) : Optional.empty();
    }

    @Override
    public Optional<ModelDefinition> findByModel(String modelId) {
        return modelId == null || modelId.isBlank()
                ? Optional.empty() : Optional.ofNullable(modelsById.get(modelId));
    }

    @Override
    public List<ModelDefinition> findByProvider(Provider provider) {
        ModelDefinition definition = provider == null ? null : modelsByProvider.get(provider);
        return definition == null ? List.of() : List.of(definition);
    }

    @Override
    public boolean exists(Provider provider, String modelId) {
        return find(provider, modelId).isPresent();
    }

    @Override
    public String defaultModel(Provider provider) {
        if (provider == null) throw new BusinessException("Provider is required to resolve default model.");
        ModelDefinition definition = modelsByProvider.get(provider);
        if (definition == null) {
            throw new BusinessException("No default model registered for provider " + provider + ".");
        }
        return definition.modelId();
    }
}
