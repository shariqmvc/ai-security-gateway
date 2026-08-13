package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.TenantDefaultRoutingStrategy;
import com.ai.gateway.routing.registry.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantDefaultRoutingStrategyTest {

    @Mock
    private ProviderModelRegistryService
            providerModelRegistryService;

    private TenantDefaultRoutingStrategy strategy;

    private AuthenticationContext authenticationContext;

    private UUID tenantId;

    @BeforeEach
    void setUp() {

        strategy =
                new TenantDefaultRoutingStrategy(
                        providerModelRegistryService);

        tenantId =
                UUID.randomUUID();

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(
                                Provider.GEMINI)
                        .defaultModel(
                                "gemini-test")
                        .build();
    }

    @Test
    void shouldSupportRequestWithoutProviderAndModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertTrue(
                strategy.supports(context));
    }

    @Test
    void shouldSupportRequestWithBlankModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .model("   ")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertTrue(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportExplicitProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .provider(Provider.GEMINI)
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldNotSupportExplicitModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
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
    void shouldNotSupportNullAuthenticationContext() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        null);

        assertFalse(
                strategy.supports(context));
    }

    @Test
    void shouldRouteUsingTenantDefaults() {

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
                        enabledModel(
                                Provider.GEMINI,
                                "gemini-test"));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingDecision decision =
                strategy.route(context);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.TENANT_DEFAULT,
                decision.strategy());

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");
    }

    @Test
    void shouldRejectMissingDefaultProvider() {

        AuthenticationContext contextWithNoProvider =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(null)
                        .defaultModel("gemini-test")
                        .build();

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        contextWithNoProvider);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService,
                never())
                .requireProvider(any());

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        any());
    }

    @Test
    void shouldRejectMissingDefaultModel() {

        AuthenticationContext contextWithNoModel =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel(null)
                        .build();

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        contextWithNoModel);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService,
                never())
                .requireProvider(any());

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        any());
    }

    @Test
    void shouldRejectBlankDefaultModel() {

        AuthenticationContext contextWithBlankModel =
                AuthenticationContext.builder()
                        .tenantId(tenantId)
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("   ")
                        .build();

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        contextWithBlankModel);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService,
                never())
                .requireProvider(any());

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        any());
    }

    @Test
    void shouldRejectUnavailableTenantDefaultProvider() {

        when(providerModelRegistryService
                .requireProvider(
                        Provider.GEMINI))
                .thenThrow(
                        new BusinessException(
                                "GEMINI provider is not available."));

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService,
                never())
                .requireModel(
                        any(),
                        any());
    }

    @Test
    void shouldRejectUnavailableTenantDefaultModel() {

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
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

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
