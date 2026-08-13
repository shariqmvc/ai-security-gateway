package com.ai.gateway.routing;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
public class ExplicitModelRoutingStrategy
        implements RoutingStrategyHandler {

    private final ModelRegistry modelRegistry;

    private final ProviderModelRegistryService
            providerModelRegistryService;

    @Override
    public boolean supports(
            RoutingContext context) {

        if (context == null
                || context.request() == null) {

            return false;
        }

        ChatRequest request =
                context.request();

        return request.getProvider() == null
                && request.getModel() != null
                && !request.getModel().isBlank();
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        String model =
                context.request()
                        .getModel();

        ModelDefinition definition =
                modelRegistry
                        .findByModel(model)
                        .filter(ModelDefinition::isEnabled)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Model "
                                                + model
                                                + " is not available."));

        Provider provider =
                definition.provider();

        providerModelRegistryService
                .requireProvider(provider);

        providerModelRegistryService
                .requireModel(
                        provider,
                        model);

        return new RoutingDecision(
                provider,
                model,
                RoutingStrategy.EXPLICIT_MODEL);
    }
}
