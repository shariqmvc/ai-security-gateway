package com.ai.gateway.core.routing.registry.impl;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIProviderFactory;
import com.ai.gateway.core.routing.registry.ProviderDefinition;
import com.ai.gateway.core.routing.registry.ProviderRegistry;
import com.ai.gateway.core.routing.registry.ProviderStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, startup-built provider registry. Provider definitions are stable
 * control-plane data, so request-path lookups do not recreate definitions or
 * repeatedly query the provider factory.
 */
@Component
public class ProviderRegistryImpl implements ProviderRegistry {

    private final Map<Provider, ProviderDefinition> definitions;
    private final List<ProviderDefinition> allDefinitions;

    public ProviderRegistryImpl(AIProviderFactory providerFactory) {
        EnumMap<Provider, ProviderDefinition> map = new EnumMap<>(Provider.class);
        List<ProviderDefinition> all = new ArrayList<>();

        for (Provider provider : Provider.values()) {
            ProviderDefinition definition;
            try {
                AIProvider implementation = providerFactory.getProvider(provider);
                if (implementation == null) throw new IllegalStateException("Provider implementation is unavailable");
                definition = new ProviderDefinition(
                        provider, provider.name(), ProviderStatus.ENABLED, Set.of("CHAT"));
            } catch (Exception ex) {
                definition = new ProviderDefinition(
                        provider, provider.name(), ProviderStatus.DISABLED, Set.of());
            }
            map.put(provider, definition);
            all.add(definition);
        }

        this.definitions = Map.copyOf(map);
        this.allDefinitions = List.copyOf(all);
    }

    @Override
    public Optional<ProviderDefinition> find(Provider provider) {
        return provider == null ? Optional.empty() : Optional.ofNullable(definitions.get(provider));
    }

    @Override
    public List<ProviderDefinition> findAll() {
        return allDefinitions;
    }

    @Override
    public boolean isEnabled(Provider provider) {
        ProviderDefinition definition = provider == null ? null : definitions.get(provider);
        return definition != null && definition.isEnabled();
    }
}
