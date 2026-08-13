package com.ai.gateway.routing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
@RequiredArgsConstructor
public class TenantDefaultRoutingStrategy implements RoutingStrategyHandler {
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

        ChatRequest request =
                context.request();

        return (request.getProvider() == null)
                && (request.getModel() == null
                || request.getModel().isBlank());
    }

    @Override
    public RoutingDecision route(
            RoutingContext context) {

        AuthenticationContext authenticationContext =
                context.authenticationContext();

        Provider provider =
                authenticationContext
                        .getDefaultProvider();

        String model =
                authenticationContext
                        .getDefaultModel();

        if (provider == null) {

            throw new BusinessException(
                    "Tenant default provider is not configured.");
        }

        if (model == null || model.isBlank()) {

            throw new BusinessException(
                    "Tenant default model is not configured.");
        }

        providerModelRegistryService
                .requireProvider(provider);

        providerModelRegistryService
                .requireModel(
                        provider,
                        model);

        return new RoutingDecision(
                provider,
                model,
                RoutingStrategy.TENANT_DEFAULT);
    }
}
