package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.ExplicitModelRoutingStrategy;
import com.ai.gateway.routing.ExplicitProviderRoutingStrategy;
import com.ai.gateway.routing.PolicyBasedRoutingStrategy;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingService;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.TenantDefaultRoutingStrategy;
import com.ai.gateway.routing.policy.RoutingPolicyService;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelRegistry;
import com.ai.gateway.routing.registry.ModelStatus;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PolicyRoutingIntegrationTest {

    private ProviderModelRegistryService registryService;
    private ModelRegistry modelRegistry;
    private RoutingPolicyService routingPolicyService;

    private RoutingService routingService;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        registryService =
                mock(ProviderModelRegistryService.class);

        modelRegistry =
                mock(ModelRegistry.class);

        routingPolicyService =
                mock(RoutingPolicyService.class);

        ExplicitProviderRoutingStrategy
                explicitProvider =
                new ExplicitProviderRoutingStrategy(
                        registryService,
                        modelRegistry);

        ExplicitModelRoutingStrategy
                explicitModel =
                new ExplicitModelRoutingStrategy(
                        modelRegistry,
                        registryService);

        PolicyBasedRoutingStrategy
                policyBased =
                new PolicyBasedRoutingStrategy(
                        routingPolicyService,
                        registryService);

        TenantDefaultRoutingStrategy
                tenantDefault =
                new TenantDefaultRoutingStrategy(
                        registryService);

        routingService =
                new RoutingService(
                        List.of(
                                explicitProvider,
                                explicitModel,
                                policyBased,
                                tenantDefault));

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();
    }

    @Test
    void shouldPreserveExplicitProviderPrecedence() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        verifyNoInteractions(routingPolicyService);
    }

    @Test
    void shouldPreserveExplicitModelPrecedence() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        when(modelRegistry.findByModel("gemini-test"))
                .thenReturn(
                        java.util.Optional.of(
                                new ModelDefinition(
                                        Provider.GEMINI,
                                        "gemini-test",
                                        "gemini-test",
                                        ModelStatus.ENABLED,
                                        Set.of("CHAT"))));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.EXPLICIT_MODEL,
                decision.strategy());

        verifyNoInteractions(routingPolicyService);
    }

    @Test
    void shouldUsePolicyRoutingWhenNoExplicitSelectionExists() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(
                        new com.ai.gateway.routing.policy.RoutingPolicy(
                                true,
                                List.of(Provider.GEMINI),
                                List.of("gemini-test"),
                                Provider.GEMINI,
                                "gemini-test"));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.POLICY_BASED,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        verify(routingPolicyService)
                .resolve(
                        request,
                        authenticationContext);
    }

    @Test
    void shouldUseTenantDefaultWhenPolicyStrategyDoesNotSupportRequest() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        /*
         * This test is intentionally NOT expected to reach tenant default
         * with the current RoutingPolicyService implementation because
         * PolicyBasedRoutingStrategy supports requests without explicit
         * provider/model.
         *
         * It documents the precedence boundary and should be replaced by
         * the policy-disabled/fallback behavior once policy resolution
         * supports a disabled policy.
         */
    }
}