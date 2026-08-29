package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.*;
import com.ai.gateway.core.routing.registry.ModelDefinition;
import com.ai.gateway.core.routing.registry.ModelRegistry;
import com.ai.gateway.core.routing.registry.ModelStatus;
import com.ai.gateway.core.routing.registry.ProviderModelRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingPrecedenceTest {

    @Mock
    private ProviderModelRegistryService registryService;

    @Mock
    private ModelRegistry modelRegistry;

    private RoutingService routingService;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

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

        TenantDefaultRoutingStrategy
                tenantDefault =
                new TenantDefaultRoutingStrategy(
                        registryService);

        routingService =
                new RoutingService(
                        List.of(
                                explicitProvider,
                                explicitModel,
                                tenantDefault));

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantId(UUID.randomUUID())
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-3.6-flash")
                        .build();
    }

    @Test
    void explicitProviderAndModelShouldUseExplicitProviderStrategy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-3.6-flash")
                        .build();

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-3.6-flash",
                decision.model());
    }

    @Test
    void explicitProviderShouldOverrideTenantDefault() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        when(modelRegistry.defaultModel(
                Provider.GEMINI))
                .thenReturn("gemini-3.6-flash");

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-3.6-flash",
                decision.model());
    }

    @Test
    void explicitModelShouldOverrideTenantDefault() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-3.6-flash")
                        .build();

        when(modelRegistry.findByModel(
                "gemini-3.6-flash"))
                .thenReturn(
                        java.util.Optional.of(
                                new ModelDefinition(
                                        Provider.GEMINI,
                                        "gemini-3.6-flash",
                                        "Gemini 3.6 Flash",
                                        ModelStatus.ENABLED,
                                        Set.of())));

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.EXPLICIT_MODEL,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-3.6-flash",
                decision.model());
    }

    @Test
    void noProviderOrModelShouldUseTenantDefault() {

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(null);

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-3.6-flash"))
                .thenReturn(null);

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingDecision decision =
                routingService.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                RoutingStrategy.TENANT_DEFAULT,
                decision.strategy());

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-3.6-flash",
                decision.model());
    }
}
