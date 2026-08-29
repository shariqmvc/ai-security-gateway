package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CandidateModelResolverImpl
        implements CandidateModelResolver {

    private final ModelRegistry modelRegistry;

    @Override
    public List<String> resolve(
            Provider provider,
            RoutingPolicy policy) {

        if (provider == null
                || policy == null
                || !policy.enabled()) {

            return List.of();
        }

        List<ModelDefinition> definitions =
                modelRegistry.findByProvider(provider);

        if (definitions == null
                || definitions.isEmpty()) {

            return List.of();
        }

        return definitions.stream()
                .filter(Objects::nonNull)
                .filter(ModelDefinition::isEnabled)
                .filter(definition ->
                        provider.equals(
                                definition.provider()))
                .map(ModelDefinition::modelId)
                .filter(Objects::nonNull)
                .filter(model -> !model.isBlank())
                .filter(policy::allowsModel)
                .distinct()
                .toList();
    }
}