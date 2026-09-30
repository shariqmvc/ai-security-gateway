package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderModelRegistryService {

    private final ProviderRegistry providerRegistry;
    private final ModelRegistry modelRegistry;

    public ProviderDefinition requireProvider(
            Provider provider) {

        return providerRegistry
                .find(provider)
                .filter(ProviderDefinition::isEnabled)
                .orElseThrow(() ->
                        new BusinessException(
                                provider +
                                        " provider is not available."));
    }

    public String defaultModel(Provider provider) {
        if (provider == null) {
            return null;
        }
        return modelRegistry.defaultModel(provider);
    }

    public ModelDefinition requireModel(
            Provider provider,
            String modelId) {

        return modelRegistry
                .find(provider, modelId)
                .filter(ModelDefinition::isEnabled)
                .orElseThrow(() ->
                        new BusinessException(
                                "Model " +
                                        modelId +
                                        " is not available for provider " +
                                        provider +
                                        "."));
    }
    public List<ModelDefinition> requireModels(
            Provider provider,
            String modelId,
            Set<String> requiredCapabilities) {

        List<ModelDefinition> models = modelRegistry.findAll(provider, modelId)
                .stream()
                .filter(ModelDefinition::isEnabled)
                .filter(model -> requiredCapabilities == null
                        || requiredCapabilities.isEmpty()
                        || model.capabilities().containsAll(requiredCapabilities))
                .toList();

        if (models.isEmpty()) {
            throw new BusinessException(
                    "Model " + modelId + " is not available for provider "
                            + provider + " with required capabilities: "
                            + requiredCapabilities);
        }

        return models;
    }

    public ModelDefinition requireModel(
            Provider provider,
            String modelId,
            Set<String> requiredCapabilities) {
        ModelDefinition model = requireModel(provider, modelId);
        if (requiredCapabilities != null && !requiredCapabilities.isEmpty()
                && !model.capabilities().containsAll(requiredCapabilities)) {
            throw new BusinessException(
                    "Model " + modelId + " for provider " + provider
                            + " does not support required capabilities: " + requiredCapabilities);
        }
        return model;
    }

}
