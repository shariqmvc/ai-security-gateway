package com.ai.gateway.routing.registry;

import com.ai.gateway.enums.Provider;

import java.util.List;
import java.util.Optional;

public interface ModelRegistry {

    Optional<ModelDefinition> find(
            Provider provider,
            String modelId);

    List<ModelDefinition> findAll();

    List<ModelDefinition> findByProvider(
            Provider provider);

    String defaultModel(Provider provider);

    boolean exists(
            Provider provider,
            String modelId);

    Optional<ModelDefinition> findByModel(
            String modelId);
}
