package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantDefaultRoutingStrategy implements RoutingStrategyHandler {
    private final ProviderModelRegistryService registryService;
    private final ModelRegistry modelRegistry;

    @Override
    public boolean supports(
            RoutingContext context) {

        return context != null
                && context.request() != null
                && context.request().getProvider() == null;
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        if (context == null
                || context.authenticationContext() == null) {

            throw new BusinessException(
                    "Authentication context is required for tenant-default routing.");
        }

        Provider provider =
                context.authenticationContext()
                        .getDefaultProvider();

        if (provider == null) {

            throw new BusinessException(
                    "Tenant default provider is not configured.");
        }

        String model =
                context.request().getModel();

        if (model == null || model.isBlank()) {

            model =
                    context.authenticationContext()
                            .getDefaultModel();
        }

        if (model == null || model.isBlank()) {

            model =
                    modelRegistry.defaultModel(provider);
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
