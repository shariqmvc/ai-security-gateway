package com.ai.gateway.routing.registry.impl;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ModelStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ModelRegistryImpl
        implements ModelRegistry {

    private final AIProviderFactory providerFactory;

    @Override
    public Optional<ModelDefinition> find(
            Provider provider,
            String modelId) {

        if (provider == null
                || modelId == null
                || modelId.isBlank()) {

            return Optional.empty();
        }

        AIProvider aiProvider;

        try {

            aiProvider =
                    providerFactory.getProvider(provider);

        } catch (Exception ex) {

            return Optional.empty();
        }

        String defaultModel =
                aiProvider.defaultModel();

        if (!modelId.equals(defaultModel)) {
            return Optional.empty();
        }

        return Optional.of(
                new ModelDefinition(
                        provider,
                        modelId,
                        modelId,
                        ModelStatus.ENABLED,
                        Set.of("CHAT")
                )
        );
    }

    @Override
    public Optional<ModelDefinition> findByModel(
            String modelId) {

        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }

        for (Provider provider : Provider.values()) {

            Optional<ModelDefinition> model =
                    find(provider, modelId);

            if (model.isPresent()) {
                return model;
            }
        }

        return Optional.empty();
    }

    @Override
    public List<ModelDefinition> findByProvider(
            Provider provider) {

        if (provider == null) {
            return List.of();
        }

        try {

            AIProvider aiProvider =
                    providerFactory.getProvider(provider);

            String model =
                    aiProvider.defaultModel();

            if (model == null || model.isBlank()) {
                return List.of();
            }

            return List.of(
                    new ModelDefinition(
                            provider,
                            model,
                            model,
                            ModelStatus.ENABLED,
                            Set.of("CHAT")
                    )
            );

        } catch (Exception ex) {

            return List.of();
        }
    }

    @Override
    public boolean exists(
            Provider provider,
            String modelId) {

        return find(provider, modelId).isPresent();
    }

    @Override
    public String defaultModel(
            Provider provider) {

        if (provider == null) {

            throw new BusinessException(
                    "Provider is required to resolve default model.");
        }

        return findByProvider(provider)
                .stream()
                .findFirst()
                .map(ModelDefinition::modelId)
                .orElseThrow(() ->
                        new BusinessException(
                                "No default model registered for provider "
                                        + provider
                                        + "."));
    }
}
