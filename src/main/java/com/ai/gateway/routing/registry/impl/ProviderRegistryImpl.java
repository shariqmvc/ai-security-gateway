package com.ai.gateway.routing.registry.impl;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.registry.ProviderDefinition;
import com.ai.gateway.routing.registry.ProviderRegistry;
import com.ai.gateway.routing.registry.ProviderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProviderRegistryImpl
        implements ProviderRegistry {

    private final AIProviderFactory providerFactory;

    @Override
    public Optional<ProviderDefinition> find(
            Provider provider) {

        if (provider == null) {
            return Optional.empty();
        }

        try {

            AIProvider aiProvider =
                    providerFactory.getProvider(provider);

            return Optional.of(
                    new ProviderDefinition(
                            provider,
                            provider.name(),
                            ProviderStatus.ENABLED,
                            Set.of("CHAT")
                    )
            );

        } catch (Exception ex) {

            return Optional.of(
                    new ProviderDefinition(
                            provider,
                            provider.name(),
                            ProviderStatus.DISABLED,
                            Set.of()
                    )
            );
        }
    }

    @Override
    public List<ProviderDefinition> findAll() {

        return Arrays.stream(Provider.values())
                .map(this::find)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public boolean isEnabled(
            Provider provider) {

        return find(provider)
                .map(ProviderDefinition::isEnabled)
                .orElse(false);
    }
}
