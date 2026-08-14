package com.ai.gateway.failover;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.AIProviderFactory;
import com.ai.gateway.routing.analytics.RoutingAnalyticsService;
import com.ai.gateway.routing.registry.ModelDefinition;
import com.ai.gateway.routing.registry.ModelStatus;
import com.ai.gateway.routing.registry.ProviderDefinition;
import com.ai.gateway.routing.registry.ProviderModelRegistryService;
import com.ai.gateway.routing.registry.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderFailoverServiceImplTest {

    @Mock
    private AIProviderFactory providerFactory;

    @Mock
    private ProviderModelRegistryService registry;

    @Mock
    private GatewayMetricsService metricsService;

    @Mock
    private AIProvider geminiProvider;

    @Mock
    private AIProvider openAiProvider;

    private FailoverProperties properties;
    private ProviderFailoverServiceImpl service;

    private AIRequest primaryRequest;
    private AIResponse response;
    @Mock
    private RoutingAnalyticsService routingAnalyticsService;


    @BeforeEach
    void setUp() {
        properties = new FailoverProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(2);
        properties.setProviders(
                new java.util.EnumMap<>(Provider.class));

        properties.getProviders().put(
                Provider.GEMINI,
                List.of(Provider.OPENAI));

        service = new ProviderFailoverServiceImpl(
                providerFactory,
                registry,
                properties,
                metricsService,
                routingAnalyticsService);

        primaryRequest = AIRequest.builder()
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .prompt("hello")
                .build();

        response = AIResponse.builder()
                .response("fallback response")
                .build();
    }

    @Test
    void shouldReturnPrimaryResponseWithoutFailover() {

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenReturn(response);

        AIResponse result = service.execute(primaryRequest);

        assertSame(response, result);

        verify(geminiProvider).chat(primaryRequest);
        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);
        verify(metricsService, never())
                .increment(MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);
        verify(metricsService, never())
                .increment(MetricsConstants.ROUTING_FAILOVER_SUCCESS);
        verifyNoInteractions(routingAnalyticsService);
    }

    @Test
    void shouldFailoverWhenPrimaryProviderFails() {

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        RuntimeException primaryFailure =
                new RuntimeException("Gemini unavailable");

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(primaryFailure);

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(enabledModel(
                        Provider.OPENAI,
                        "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        AIResponse result = service.execute(primaryRequest);

        assertSame(response, result);

        verify(geminiProvider).chat(primaryRequest);

        verify(openAiProvider).chat(
                argThat(request ->
                        request.getProvider() == Provider.OPENAI
                                && request.getModel().equals("gpt-test")
                                && request.getPrompt().equals("hello")));

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_SUCCESS);

        verify(routingAnalyticsService)
                .recordFailoverAttempt();

        verify(routingAnalyticsService)
                .recordFailoverSuccess();

        verify(routingAnalyticsService, never())
                .recordFailoverFailure();
    }

    @Test
    void shouldNotFailoverWhenDisabled() {

        properties.setEnabled(false);

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        RuntimeException failure =
                new RuntimeException("Gemini unavailable");

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertSame(failure, thrown);

        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);
        verifyNoInteractions(registry);

        verifyNoInteractions(routingAnalyticsService);
    }

    @Test
    void shouldPropagatePrimaryFailureWhenNoFallbackConfigured() {

        properties.getProviders().clear();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        RuntimeException failure =
                new RuntimeException("Gemini unavailable");

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertSame(failure, thrown);
        verifyNoInteractions(registry);
        verifyNoInteractions(routingAnalyticsService);
    }

    @Test
    void shouldPreservePrimaryFailureWhenFallbackAlsoFails() {

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        RuntimeException primaryFailure =
                new RuntimeException("Gemini unavailable");

        RuntimeException fallbackFailure =
                new RuntimeException("OpenAI unavailable");

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(primaryFailure);

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(enabledModel(
                        Provider.OPENAI,
                        "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenThrow(fallbackFailure);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertSame(primaryFailure, thrown);
        assertTrue(
                List.of(thrown.getSuppressed())
                        .contains(fallbackFailure));

        verify(routingAnalyticsService)
                .recordFailoverAttempt();

        verify(routingAnalyticsService)
                .recordFailoverFailure();

        verify(routingAnalyticsService, never())
                .recordFailoverSuccess();
    }

    @Test
    void shouldRespectMaximumAttempts() {

        properties.setMaxAttempts(2);

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(
                        new RuntimeException("Gemini unavailable"));

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(enabledModel(
                        Provider.OPENAI,
                        "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenThrow(
                        new RuntimeException("OpenAI unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> service.execute(primaryRequest));

        verify(geminiProvider, times(1))
                .chat(primaryRequest);

        verify(openAiProvider, times(1))
                .chat(any(AIRequest.class));

        verify(providerFactory, never())
                .getProvider(Provider.CLAUDE);
        verify(routingAnalyticsService)
                .recordFailoverAttempt();

        verify(routingAnalyticsService)
                .recordFailoverFailure();

        verify(routingAnalyticsService, never())
                .recordFailoverSuccess();

    }

    @Test
    void shouldSkipDuplicateFallbackProviders() {

        properties.setMaxAttempts(3);

        properties.getProviders().put(
                Provider.GEMINI,
                List.of(
                        Provider.OPENAI,
                        Provider.OPENAI));

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(
                        new RuntimeException("Gemini unavailable"));

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(enabledModel(
                        Provider.OPENAI,
                        "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenThrow(
                        new RuntimeException("OpenAI unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> service.execute(primaryRequest));

        verify(openAiProvider, times(1))
                .chat(any(AIRequest.class));

        verify(routingAnalyticsService)
                .recordFailoverAttempt();

        verify(routingAnalyticsService)
                .recordFailoverFailure();

        verify(routingAnalyticsService, never())
                .recordFailoverSuccess();
    }

    private ProviderDefinition enabledProvider(Provider provider) {
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
