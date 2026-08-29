package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.core.routing.ExplicitModelRoutingStrategy;
import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.RoutingDecision;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.routing.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExplicitModelRoutingStrategyTest {

    @Mock
    private ModelRegistry modelRegistry;

    @Mock
    private ProviderModelRegistryService
            providerModelRegistryService;

    private ExplicitModelRoutingStrategy strategy;

    private AuthenticationContext authenticationContext;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        strategy =
                new ExplicitModelRoutingStrategy(
                        modelRegistry,
                        providerModelRegistryService);

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
    void shouldSupportExplicitModelWithoutProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertTrue(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportWhenProviderIsExplicitlySpecified() {

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

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportWhenModelIsMissing() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportBlankModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("   ")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportNullContext() {

        assertFalse(
                strategy.supports(null));
    }

    @Test
    void shouldNotSupportNullRequest() {

        RoutingContext context =
                new RoutingContext(
                        null,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldResolveProviderFromExplicitModel() {

        ModelDefinition modelDefinition =
                enabledModel(
                        Provider.GEMINI,
                        "gemini-test");

        when(modelRegistry.findByModel(
                "gemini-test"))
                .thenReturn(
                        java.util.Optional.of(
                                modelDefinition));

        when(providerModelRegistryService
                .requireProvider(
                        Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(providerModelRegistryService
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test"))
                .thenReturn(
                        modelDefinition);

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        RoutingDecision decision =
                strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext));

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.EXPLICIT_MODEL,
                decision.strategy());
    }

    @Test
    void shouldRejectUnknownModel() {

        when(modelRegistry.findByModel(
                "unknown-model"))
                .thenReturn(
                        java.util.Optional.empty());

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("unknown-model")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext)));

        verify(providerModelRegistryService,
                never())
                .requireProvider(any());

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        anyString());
    }

    @Test
    void shouldRejectDisabledModel() {

        ModelDefinition disabledModel =
                new ModelDefinition(
                        Provider.GEMINI,
                        "gemini-test",
                        "gemini-test",
                        ModelStatus.DISABLED,
                        Set.of("CHAT"));

        when(modelRegistry.findByModel(
                "gemini-test"))
                .thenReturn(
                        java.util.Optional.of(
                                disabledModel));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext)));

        verify(providerModelRegistryService,
                never())
                .requireProvider(any());
    }

    @Test
    void shouldRejectUnavailableProvider() {

        ModelDefinition modelDefinition =
                enabledModel(
                        Provider.GEMINI,
                        "gemini-test");

        when(modelRegistry.findByModel(
                "gemini-test"))
                .thenReturn(
                        java.util.Optional.of(
                                modelDefinition));

        when(providerModelRegistryService
                .requireProvider(
                        Provider.GEMINI))
                .thenThrow(
                        new BusinessException(
                                "GEMINI provider is not available."));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext)));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        anyString());
    }

    @Test
    void shouldRejectUnavailableModelForResolvedProvider() {

        ModelDefinition modelDefinition =
                enabledModel(
                        Provider.GEMINI,
                        "gemini-test");

        when(modelRegistry.findByModel(
                "gemini-test"))
                .thenReturn(
                        java.util.Optional.of(
                                modelDefinition));

        when(providerModelRegistryService
                .requireProvider(
                        Provider.GEMINI))
                .thenReturn(
                        enabledProvider(
                                Provider.GEMINI));

        when(providerModelRegistryService
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test"))
                .thenThrow(
                        new BusinessException(
                                "Model gemini-test is not available for provider GEMINI."));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("gemini-test")
                        .build();

        assertThrows(
                BusinessException.class,
                () -> strategy.route(
                        new RoutingContext(
                                request,
                                authenticationContext)));

        verify(providerModelRegistryService)
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
                Set.of("CHAT"));
    }

    private ModelDefinition enabledModel(
            Provider provider,
            String model) {

        return new ModelDefinition(
                provider,
                model,
                model,
                ModelStatus.ENABLED,
                Set.of("CHAT"));
    }
}