package com.ai.gateway.failover;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.metrics.MetricsConstants;
import com.ai.gateway.observability.PerformanceLogger;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import com.ai.gateway.multimodal.MediaInputException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
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

    @Mock
    private RoutingAnalyticsService routingAnalyticsService;

    @Mock
    private PerformanceLogger performanceLogger;

    @Mock
    private ProviderCircuitBreaker providerCircuitBreaker;

    private FailoverProperties properties;
    private ProviderFailoverServiceImpl service;

    private AIRequest primaryRequest;
    private AIResponse response;

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
                routingAnalyticsService,
                performanceLogger);

        ReflectionTestUtils.setField(
                service,
                "providerCircuitBreaker",
                providerCircuitBreaker);

        primaryRequest = AIRequest.builder()
                .provider(Provider.GEMINI)
                .model("gemini-test")
                .prompt("hello")
                .build();

        response = AIResponse.builder()
                .response("fallback response")
                .build();
    }

    /**
     * Enables the primary provider circuit for tests whose
     * purpose is to exercise normal provider execution.
     *
     * We intentionally do this per test instead of globally in
     * setUp() so Mockito strict stubbing remains enabled and
     * tests such as shouldNotFailoverWhenDisabled() do not receive
     * an unused stubbing.
     */
    private void allowPrimaryCircuit() {
        when(providerCircuitBreaker.allowRequest(any(), any()))
                .thenReturn(true);
    }


    @Test
    void shouldNotFailoverOrMarkProviderUnhealthyForMediaInputFailure() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        MediaInputException failure =
                new MediaInputException(
                        "Unable to fetch media URL: HTTP 403.");

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        MediaInputException thrown =
                assertThrows(
                        MediaInputException.class,
                        () -> service.execute(primaryRequest));

        assertSame(failure, thrown);

        verify(geminiProvider).chat(primaryRequest);

        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);

        verify(metricsService, never())
                .increment(MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);

        verify(routingAnalyticsService, never())
                .recordFailoverAttempt();

        verify(routingAnalyticsService, never())
                .recordFailoverFailure();

        verify(providerCircuitBreaker, never())
                .recordFailure(
                        any(),
                        any(),
                        any(ProviderFailureCategory.class));
    }

    @Test
    void shouldReturnPrimaryResponseWithoutFailover() {

        allowPrimaryCircuit();

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

        allowPrimaryCircuit();

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
                .thenReturn(
                        enabledModel(
                                Provider.OPENAI,
                                "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        AIResponse result = service.execute(primaryRequest);

        assertSame(response, result);

        verify(geminiProvider)
                .chat(primaryRequest);

        verify(openAiProvider)
                .chat(argThat(request ->
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
    void shouldStopFailoverWhenPrimaryExhaustsGlobalRequestBudget() {

        allowPrimaryCircuit();

        properties.setRequestTimeBudget(
                java.time.Duration.ofMillis(100));

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenAnswer(invocation -> {
                    Thread.sleep(150L);
                    return response;
                });

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertInstanceOf(
                com.ai.gateway.config.ProviderRequestBudgetExceededException.class,
                thrown);

        verify(geminiProvider)
                .chat(primaryRequest);

        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);

        verify(providerCircuitBreaker, never())
                .recordFailure(
                        any(),
                        any(),
                        any(ProviderFailureCategory.class));

        verify(routingAnalyticsService, never())
                .recordFailoverAttempt();

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);
    }

    @Test
    void shouldRejectProviderResponseThatArrivesAfterGlobalDeadline() {

        allowPrimaryCircuit();

        properties.setRequestTimeBudget(
                java.time.Duration.ofMillis(100));

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenAnswer(invocation -> {
                    Thread.sleep(150L);
                    return response;
                });

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertInstanceOf(
                com.ai.gateway.config.ProviderRequestBudgetExceededException.class,
                thrown);

        verify(geminiProvider)
                .chat(primaryRequest);

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_BUDGET_EXHAUSTED);

        verify(providerCircuitBreaker, never())
                .recordFailure(
                        any(),
                        any(),
                        any(ProviderFailureCategory.class));
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

        allowPrimaryCircuit();

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

        allowPrimaryCircuit();

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
                .thenReturn(
                        enabledModel(
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

        allowPrimaryCircuit();

        properties.setMaxAttempts(2);

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(
                        new RuntimeException(
                                "Gemini unavailable"));

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(
                        enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(
                        enabledModel(
                                Provider.OPENAI,
                                "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenThrow(
                        new RuntimeException(
                                "OpenAI unavailable"));

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

        allowPrimaryCircuit();

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
                        new RuntimeException(
                                "Gemini unavailable"));

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(
                        enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(
                        enabledModel(
                                Provider.OPENAI,
                                "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenThrow(
                        new RuntimeException(
                                "OpenAI unavailable"));

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

    @Test
    void shouldSkipPrimaryWhenCircuitIsOpenAndFailOverImmediately() {

        // Primary circuit is OPEN.
        when(providerCircuitBreaker.allowRequest(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(false);

        when(providerCircuitBreaker.retryAfterMs(
                Provider.GEMINI,
                "gemini-test"))
                .thenReturn(60_000L);

        // Fallback circuit is CLOSED/available.
        when(providerCircuitBreaker.allowRequest(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(true);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(
                        enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(
                        enabledModel(
                                Provider.OPENAI,
                                "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        AIResponse result =
                service.execute(primaryRequest);

        assertSame(response, result);

        // Primary must never be instantiated/executed.
        verify(providerFactory, never())
                .getProvider(Provider.GEMINI);

        // Fallback must execute.
        verify(openAiProvider)
                .chat(any(AIRequest.class));

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);

        verify(metricsService)
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_SUCCESS);
    }

    @Test
    void shouldNotOpenCircuitForNonRetryableClientError() {

        allowPrimaryCircuit();

        HttpClientErrorException failure =
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        null,
                        null);

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        HttpClientErrorException thrown =
                assertThrows(
                        HttpClientErrorException.class,
                        () -> service.execute(primaryRequest));

        assertSame(failure, thrown);

        verify(providerCircuitBreaker, never())
                .recordFailure(
                        any(),
                        any(),
                        any(ProviderFailureCategory.class));

        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);

        verifyNoInteractions(registry);

        verifyNoInteractions(routingAnalyticsService);
    }

    @Test
    void shouldNotFailoverOnNonRetryableHttp400() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        HttpClientErrorException failure =
                HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST,
                        "Bad Request",
                        HttpHeaders.EMPTY,
                        null,
                        null);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        HttpClientErrorException thrown =
                assertThrows(
                        HttpClientErrorException.class,
                        () -> service.execute(primaryRequest));

        assertSame(failure, thrown);

        verify(providerFactory, never())
                .getProvider(Provider.OPENAI);

        verifyNoInteractions(registry);

        verifyNoInteractions(routingAnalyticsService);
    }

    @Test
    void shouldAttachActualFallbackProviderAndModelToResponse() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(
                        new RuntimeException(
                                "Gemini unavailable"));

        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");

        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(
                        enabledProvider(Provider.OPENAI));

        when(registry.requireModel(
                Provider.OPENAI,
                "gpt-test"))
                .thenReturn(
                        enabledModel(
                                Provider.OPENAI,
                                "gpt-test"));

        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(
                        AIResponse.builder()
                                .response("fallback")
                                .build());

        AIResponse result =
                service.execute(primaryRequest);

        assertEquals(
                Provider.OPENAI,
                result.getProvider());

        assertEquals(
                "gpt-test",
                result.getModel());
    }

    @Test
    void shouldFailoverOnProviderServerError() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);
        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        org.springframework.web.client.HttpServerErrorException serverFailure =
                org.springframework.web.client.HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Provider unavailable",
                        HttpHeaders.EMPTY,
                        null,
                        null);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(serverFailure);
        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");
        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));
        when(registry.requireModel(Provider.OPENAI, "gpt-test"))
                .thenReturn(enabledModel(Provider.OPENAI, "gpt-test"));
        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        assertSame(response, service.execute(primaryRequest));

        verify(geminiProvider).chat(primaryRequest);
        verify(openAiProvider).chat(any(AIRequest.class));
        verify(metricsService).increment(MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);
        verify(metricsService).increment(MetricsConstants.ROUTING_FAILOVER_SUCCESS);
        verify(providerCircuitBreaker).recordFailure(
                eq(Provider.GEMINI),
                eq("gemini-test"),
                eq(ProviderFailureCategory.SERVER_ERROR));
    }

    @Test
    void shouldFailoverOnRateLimitAndUseRetryAfterForCircuitDuration() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);
        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        HttpClientErrorException rateLimited =
                HttpClientErrorException.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limited",
                        retryAfterHeaders(),
                        null,
                        null);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(rateLimited);
        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");
        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));
        when(registry.requireModel(Provider.OPENAI, "gpt-test"))
                .thenReturn(enabledModel(Provider.OPENAI, "gpt-test"));
        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        assertSame(response, service.execute(primaryRequest));

        verify(providerCircuitBreaker).recordFailure(
                eq(Provider.GEMINI),
                eq("gemini-test"),
                eq(ProviderFailureCategory.RATE_LIMITED),
                eq(7000L));
        verify(openAiProvider).chat(any(AIRequest.class));
    }

    @Test
    void shouldFailoverOnWrappedProviderTimeout() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);
        when(providerFactory.getProvider(Provider.OPENAI))
                .thenReturn(openAiProvider);

        RuntimeException timeout =
                new RuntimeException(
                        "provider call failed",
                        new java.net.SocketTimeoutException("Read timed out"));

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(timeout);
        when(openAiProvider.defaultModel())
                .thenReturn("gpt-test");
        when(registry.requireProvider(Provider.OPENAI))
                .thenReturn(enabledProvider(Provider.OPENAI));
        when(registry.requireModel(Provider.OPENAI, "gpt-test"))
                .thenReturn(enabledModel(Provider.OPENAI, "gpt-test"));
        when(openAiProvider.chat(any(AIRequest.class)))
                .thenReturn(response);

        assertSame(response, service.execute(primaryRequest));

        verify(providerCircuitBreaker).recordFailure(
                eq(Provider.GEMINI),
                eq("gemini-test"),
                eq(ProviderFailureCategory.TIMEOUT));
        verify(openAiProvider).chat(any(AIRequest.class));
    }

    @Test
    void shouldNotFailoverWhenFallbackIsUnavailable() {

        allowPrimaryCircuit();

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);

        when(geminiProvider.chat(primaryRequest))
                .thenThrow(new RuntimeException("Gemini unavailable"));

        when(registry.requireProvider(Provider.OPENAI))
                .thenThrow(
                        new IllegalStateException(
                                "OPENAI disabled"));

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () -> service.execute(primaryRequest));

        assertEquals(
                "Gemini unavailable",
                thrown.getMessage());

        verify(openAiProvider, never())
                .chat(any(AIRequest.class));

        verify(metricsService, never())
                .increment(
                        MetricsConstants.ROUTING_FAILOVER_ATTEMPTS);
    }

    private HttpHeaders retryAfterHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "7");
        return headers;
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
    @Test
    void shouldMapUpstreamProviderAuthenticationFailureToGatewayException() {

        allowPrimaryCircuit();

        HttpClientErrorException failure =
                HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        HttpHeaders.EMPTY,
                        null,
                        null);

        when(providerFactory.getProvider(Provider.GEMINI))
                .thenReturn(geminiProvider);
        when(geminiProvider.chat(primaryRequest))
                .thenThrow(failure);

        ProviderAuthenticationException thrown =
                assertThrows(
                        ProviderAuthenticationException.class,
                        () -> service.execute(primaryRequest));

        assertEquals(Provider.GEMINI, thrown.getProvider());
        assertSame(failure, thrown.getCause());
        verify(providerFactory, never()).getProvider(Provider.OPENAI);
    }

}