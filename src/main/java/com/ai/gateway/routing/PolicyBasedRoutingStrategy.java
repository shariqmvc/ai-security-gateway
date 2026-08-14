package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.policy.RoutingPolicyService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class PolicyBasedRoutingStrategy
        implements RoutingStrategyHandler {

    private final RoutingPolicyService routingPolicyService;

    private final ProviderModelRegistryService
            providerModelRegistryService;

    @Override
    public boolean supports(
            RoutingContext context) {

        if (context == null
                || context.request() == null
                || context.authenticationContext() == null) {

            return false;
        }

        /*
         * Step 2 intentionally supports only requests where the
         * caller has not explicitly selected a provider or model.
         *
         * Explicit provider/model routing remains higher priority.
         */
        return context.request().getProvider() == null
                && (context.request().getModel() == null
                || context.request().getModel().isBlank());
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        RoutingPolicy policy =
                routingPolicyService.resolve(
                        context.request(),
                        context.authenticationContext());

        if (policy == null) {
            throw new BusinessException(
                    "Routing policy could not be resolved.");
        }

        if (!policy.enabled()) {
            throw new BusinessException(
                    "Routing policy is disabled.");
        }

        Provider provider =
                policy.preferredProvider();

        String model =
                policy.preferredModel();

        if (provider == null) {
            throw new BusinessException(
                    "Routing policy does not define a provider.");
        }

        if (model == null || model.isBlank()) {
            throw new BusinessException(
                    "Routing policy does not define a model.");
        }

        if (!policy.allowsProvider(provider)) {
            throw new BusinessException(
                    "Provider "
                            + provider
                            + " is not allowed by routing policy.");
        }

        if (!policy.allowsModel(model)) {
            throw new BusinessException(
                    "Model "
                            + model
                            + " is not allowed by routing policy.");
        }

        providerModelRegistryService.requireProvider(
                provider);

        providerModelRegistryService.requireModel(
                provider,
                model);

        return new RoutingDecision(
                provider,
                model,
                RoutingStrategy.POLICY_BASED);
    }
}