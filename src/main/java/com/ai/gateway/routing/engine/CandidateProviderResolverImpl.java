package com.ai.gateway.routing.engine;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.registry.ProviderDefinition;
import com.ai.gateway.routing.registry.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CandidateProviderResolverImpl implements CandidateProviderResolver{
    private final ProviderRegistry providerRegistry;

    @Override
    public List<Provider> resolve(RoutingPolicy policy) {

        if (policy == null || !policy.enabled()) {
            return List.of();
        }

        List<ProviderDefinition> definitions =
                providerRegistry.findAll();

        if (definitions == null || definitions.isEmpty()) {
            return List.of();
        }

        return definitions.stream()
                .filter(Objects::nonNull)
                .filter(ProviderDefinition::isEnabled)
                .map(ProviderDefinition::provider)
                .filter(Objects::nonNull)
                .filter(policy::allowsProvider)
                .distinct()
                .toList();
    }
}
