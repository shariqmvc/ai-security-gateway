package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.routing.PolicyBasedRoutingStrategy;
import com.ai.gateway.routing.RoutingContext;
import com.ai.gateway.routing.RoutingDecision;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.routing.policy.RoutingPolicy;
import com.ai.gateway.routing.policy.RoutingPolicyService;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyBasedRoutingStrategyTest {

    @Mock
    private RoutingPolicyService routingPolicyService;

    @Mock
    private ProviderModelRegistryService
            providerModelRegistryService;

    private PolicyBasedRoutingStrategy strategy;

    private AuthenticationContext authenticationContext;

    @BeforeEach
    void setUp() {

        strategy =
                new PolicyBasedRoutingStrategy(
                        routingPolicyService,
                        providerModelRegistryService);

        authenticationContext =
                AuthenticationContext.builder()
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .build();
    }

    @Test
    void shouldSupportRequestWithoutExplicitProviderOrModel() {

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
    void shouldNotSupportExplicitProviderRequest() {

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
    void shouldNotSupportExplicitModelRequest() {

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
    void shouldRouteUsingPolicyPreferredProviderAndModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        RoutingDecision decision =
                strategy.route(context);

        assertNotNull(decision);

        assertEquals(
                Provider.GEMINI,
                decision.provider());

        assertEquals(
                "gemini-test",
                decision.model());

        assertEquals(
                RoutingStrategy.POLICY_BASED,
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
    void shouldRejectNullPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(null);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldRejectDisabledPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        false,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldRejectPolicyWithoutProvider() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        null,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldRejectPolicyWithoutModel() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(),
                        List.of(),
                        Provider.GEMINI,
                        null);

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldRejectPreferredProviderNotAllowedByPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.OPENAI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldRejectPreferredModelNotAllowedByPolicy() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("other-model"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verifyNoInteractions(
                providerModelRegistryService);
    }

    @Test
    void shouldPropagateProviderRegistryFailure() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        doThrow(new BusinessException(
                "GEMINI provider is not available."))
                .when(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        assertThrows(
                BusinessException.class,
                () -> strategy.route(context));

        verify(providerModelRegistryService)
                .requireProvider(
                        Provider.GEMINI);

        verify(providerModelRegistryService, never())
                .requireModel(
                        any(),
                        anyString());
    }

    @Test
    void shouldPropagateModelRegistryFailure() {

        ChatRequest request =
                ChatRequest.builder()
                        .prompt("hello")
                        .build();

        RoutingContext context =
                new RoutingContext(
                        request,
                        authenticationContext);

        RoutingPolicy policy =
                new RoutingPolicy(
                        true,
                        List.of(Provider.GEMINI),
                        List.of("gemini-test"),
                        Provider.GEMINI,
                        "gemini-test");

        when(routingPolicyService.resolve(
                request,
                authenticationContext))
                .thenReturn(policy);

        doThrow(new BusinessException(
                "Model gemini-test is not available."))
                .when(providerModelRegistryService)
                .requireModel(
                        Provider.GEMINI,
                        "gemini-test");

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
}