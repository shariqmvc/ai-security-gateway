package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantDefaultRoutingStrategy implements RoutingStrategyHandler {
    private final ProviderModelRegistryService registryService;

    @Override
    public boolean supports(
            RoutingContext context) {

        return context.request().getProvider() == null;
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        Provider provider =
                context.authenticationContext()
                        .getDefaultProvider();

        String model =
                context.request().getModel();

        if (model == null || model.isBlank()) {

            model =
                    context.authenticationContext()
                            .getDefaultModel();
        }

        registryService.requireProvider(provider);

        registryService.requireModel(
                provider,
                model);

        return new RoutingDecision(
                provider,
                model,
                RoutingStrategy.TENANT_DEFAULT);
    }
}
