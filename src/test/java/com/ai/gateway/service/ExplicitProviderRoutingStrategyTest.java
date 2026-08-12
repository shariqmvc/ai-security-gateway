package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.ExplicitProviderRoutingStrategy;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplicitProviderRoutingStrategyTest {

    @Mock
    private ProviderModelRegistryService registryService;

    @Mock
    private ModelRegistry modelRegistry;

    private ExplicitProviderRoutingStrategy strategy;

    private UUID tenantId;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        strategy =
                new ExplicitProviderRoutingStrategy(
                        registryService,
                        modelRegistry);

        tenantId =
                UUID.randomUUID();

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(
                                Provider.OPENAI)
                        .defaultModel(
                                "openai-model")
                        .build();
    }

    @Test
    void shouldSupportRequestWithExplicitProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertEquals(
                true,
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportRequestWithoutExplicitProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertEquals(
                false,
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportNullContext() {

        assertEquals(
                false,
                strategy.supports(null));
    }

    @Test
    void shouldNotSupportContextWithNullRequest() {

        RoutingContext context =
                new RoutingContext(
                        null,
                        authenticationContext);

        assertEquals(
                false,
                strategy.supports(context));
    }

    @Test
    void shouldRouteUsingExplicitProviderAndModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(
                        enabledModel(
                                Provider.GEMINI,
                                "gemini-test"));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        verify(registryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(registryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");

        verify(modelRegistry, never())
                .defaultModel(any());
    }

    @Test
    void shouldUseSelectedProviderDefaultModelWhenModelIsOmitted() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(modelRegistry.defaultModel(
                Provider.GEMINI))
                .thenReturn(
                        "gemini-test");

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(
                        enabledModel(
                                Provider.GEMINI,
                                "gemini-test"));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        /*
         * Important:
         *
         * Tenant default is:
         *
         * OPENAI / openai-model
         *
         * Requested provider is:
         *
         * GEMINI
         *
         * Therefore the model must come from
         * GEMINI's registry, not the tenant's
         * default model.
         */
        assertEquals(
                "gemini-test",
                decision.model());

        verify(modelRegistry)
                .defaultModel(
                        Provider.GEMINI);

        verify(registryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");
    }

    @Test
    void shouldPreferExplicitModelOverProviderDefaultModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-custom")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-custom"))
                .thenReturn(
                        enabledModel(
                                Provider.GEMINI,
                                "gemini-custom"));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-custom",
                decision.model());

        assertEquals(
                RoutingStrategy.EXPLICIT_PROVIDER,
                decision.strategy());

        verify(modelRegistry, never())
                .defaultModel(any());

        verify(registryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-custom");
    }

    @Test
    void shouldTreatBlankModelAsOmitted() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("   ")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(modelRegistry.defaultModel(
                Provider.GEMINI))
                .thenReturn(
                        "gemini-test");

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(
                        enabledModel(
                                Provider.GEMINI,
                                "gemini-test"));

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                "gemini-test",
                decision.model());

        verify(modelRegistry)
                .defaultModel(
                        Provider.GEMINI);
    }

    @Test
    void shouldRejectUnavailableProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenThrow(
                        new BusinessException(
                                "GEMINI provider is not available."));

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(registryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(registryService,
                never())
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");

        verify(modelRegistry,
                never())
                .defaultModel(any());
    }

    @Test
    void shouldRejectUnavailableExplicitModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .model("unknown-model")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(registryService.requireModel(
                Provider.GEMINI,
                "unknown-model"))
                .thenThrow(
                        new BusinessException(
                                "Model unknown-model is not available for provider GEMINI."));

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(registryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(registryService)
                .requireModel(
                        Provider.GEMINI,
                        "unknown-model");

        verify(modelRegistry,
                never())
                .defaultModel(any());
    }

    @Test
    void shouldRejectUnavailableProviderDefaultModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(registryService.requireProvider(
                Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(modelRegistry.defaultModel(
                Provider.GEMINI))
                .thenReturn(
                        "gemini-test");

        when(registryService.requireModel(
                Provider.GEMINI,
                "gemini-test"))
                .thenThrow(
                        new BusinessException(
                                "Model gemini-test is not available for provider GEMINI."));

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(modelRegistry)
                .defaultModel(
                        Provider.GEMINI);

        verify(registryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");
    }

    private ProviderDefinition enabledProvider(
            Provider provider) {

        return new ProviderDefinition(
                provider,
                provider.name(),
                ProviderStatus.ENABLED,
                Set.of(
                        ModelCapabilities.CHAT));
    }

    private ModelDefinition enabledModel(
            Provider provider,
            String model) {

        return new ModelDefinition(
                provider,
                model,
                model,
                ModelStatus.ENABLED,
                Set.of(
                        ModelCapabilities.CHAT));
    }
}
