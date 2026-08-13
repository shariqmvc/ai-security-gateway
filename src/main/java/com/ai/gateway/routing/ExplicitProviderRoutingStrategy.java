package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
public class ExplicitProviderRoutingStrategy
        implements RoutingStrategyHandler {

    private final ProviderModelRegistryService registryService;

    private final ModelRegistry modelRegistry;

    @Override
    public boolean supports(
            RoutingContext context) {

        return context != null
                && context.request() != null
                && context.request().getProvider() != null;
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        Provider provider =
                context.request().getProvider();

        registryService.requireProvider(provider);

        String model =
                context.request().getModel();

        if (model == null || model.isBlank()) {

            model =
                    modelRegistry.defaultModel(provider);
        }

        registryService.requireModel(
                provider,
                model);

        return new RoutingDecision(
                provider,
                model,
                RoutingStrategy.EXPLICIT_PROVIDER);
    }
}
