package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;

import java.util.List;
import java.util.Optional;

public interface ProviderRegistry {
    Optional<ProviderDefinition> find(Provider provider);

    List<ProviderDefinition> findAll();

    boolean isEnabled(Provider provider);
}
