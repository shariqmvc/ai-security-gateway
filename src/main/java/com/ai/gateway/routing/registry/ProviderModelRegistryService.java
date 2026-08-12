package com.ai.gateway.routing.registry;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
