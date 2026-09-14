package com.ai.gateway.core.routing.policy;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class RoutingPolicyServiceImpl
        implements RoutingPolicyService {

    private final ModelRegistry modelRegistry;

    /** Compatibility constructor retained for isolated routing tests. */
    public RoutingPolicyServiceImpl() {
        this.modelRegistry = null;
    }

    @Autowired
    public RoutingPolicyServiceImpl(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public RoutingPolicy resolve(
            ChatRequest request,
            AuthenticationContext authenticationContext) {

        if (authenticationContext == null) {
            throw new IllegalArgumentException(
                    "Authentication context is required.");
        }

        /*
         * Business keeps its existing tenant-default policy. Personal has no
         * tenantId/default-provider by design, so its initial policy is built
         * from the global provider/model registry. This keeps routing in Core
         * without introducing a Core -> Personal dependency.
         */
        if (authenticationContext.isPersonalPrincipal()) {
            return personalDefaultPolicy();
        }

        return new RoutingPolicy(
                true,
                java.util.List.of(),
                java.util.List.of(),
                authenticationContext.getDefaultProvider(),
                authenticationContext.getDefaultModel());
    }
    private RoutingPolicy personalDefaultPolicy() {
        // Prefer local Ollama for Personal when configured; it is the natural
        // zero-provider-key fallback. If unavailable, use the first registered
        // model from a deterministic provider order.
        for (Provider provider : new Provider[]{Provider.OLLAMA, Provider.OPENAI, Provider.GEMINI, Provider.CLAUDE}) {
            var models = modelRegistry.findByProvider(provider);
            if (models != null && !models.isEmpty()) {
                ModelDefinition definition = models.get(0);
                return new RoutingPolicy(
                        true, java.util.List.of(), java.util.List.of(),
                        definition.provider(), definition.modelId());
            }
        }
        throw new com.ai.gateway.exception.BusinessException(
                "No provider/model is available for Personal routing.");
    }

}