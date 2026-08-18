package com.ai.gateway.routing;

import com.ai.gateway.authentication.AuthenticationContext;
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

        /*
         * Explicit provider takes precedence.
         *
         * If the caller did not specify a model, first use the
         * provider/model registry default. This is the preferred
         * source because the provider was explicitly selected.
         */
        if (model == null || model.isBlank()) {

            model =
                    modelRegistry.defaultModel(provider);
        }

        /*
         * Some integration/test environments may not have a
         * provider-specific default configured yet. When the
         * authenticated tenant explicitly has a default model
         * for the same provider, use that as the compatibility
         * fallback.
         *
         * We deliberately do NOT blindly use the tenant default
         * model for a different provider.
         */
        if ((model == null || model.isBlank())
                && context.authenticationContext() != null) {

            AuthenticationContext authenticationContext =
                    context.authenticationContext();

            if (provider.equals(
                    authenticationContext.getDefaultProvider())) {

                model =
                        authenticationContext.getDefaultModel();
            }
        }

        /*
         * Do not allow an invalid RoutingCandidate to be created.
         * The registry validation gives the caller the appropriate
         * business-level error when no model can be resolved.
         */
        if (model == null || model.isBlank()) {

            throw new IllegalArgumentException(
                    "Model is required for explicitly selected provider "
                            + provider + ".");
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