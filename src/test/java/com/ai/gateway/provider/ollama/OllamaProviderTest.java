package com.ai.gateway.provider.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.config.ProviderConcurrencyLimiter;
import com.ai.gateway.config.ProviderConcurrencyProperties;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.observability.PerformanceLogger;
import com.ai.gateway.provider.AIStreamResult;
import com.ai.gateway.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.provider.ollama.dto.OllamaOptions;
import com.ai.gateway.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.provider.ollama.dto.OllamaResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OllamaProviderTest {

    @Test
    void sendsLatencyControlsAndMapsOllamaTelemetry() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);

        OllamaConfig config = new OllamaConfig();
        config.setModel("llama3.1:8b");
        config.setNumCtx(4096);
        config.setNumPredict(512);
        config.setKeepAlive("10m");

        ProviderConcurrencyProperties concurrencyProperties =
                new ProviderConcurrencyProperties();
        concurrencyProperties.setProviders(new EnumMap<>(Provider.class));

        ProviderConcurrencyLimiter limiter = new ProviderConcurrencyLimiter(
                concurrencyProperties,
                performanceLogger);

        OllamaResponse response = new OllamaResponse(
                OllamaMessage.builder()
                        .role("assistant")
                        .content("Tenant isolation prevents cross-tenant retrieval.")
                        .build(),
                2_500_000_000L,
                100_000_000L,
                24,
                30_000_000L,
                40,
                2_000_000_000L);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(OllamaResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        OllamaProvider provider = new OllamaProvider(
                restTemplate,
                config,
                performanceLogger,
                limiter,
                objectMapper);

        AIResponse result = provider.chat(
                AIRequest.builder()
                        .provider(Provider.OLLAMA)
                        .model("llama3.1:8b")
                        .prompt("Explain tenant isolation.")
                        .build());

        assertEquals("Tenant isolation prevents cross-tenant retrieval.", result.getResponse());
        assertEquals(Provider.OLLAMA, result.getProvider());
        assertEquals("llama3.1:8b", result.getModel());
        assertEquals(24, result.getUsage().getInputTokens());
        assertEquals(40, result.getUsage().getOutputTokens());
        assertEquals(64, result.getUsage().getTotalTokens());

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(OllamaResponse.class));

        OllamaRequest request = (OllamaRequest) captor.getValue().getBody();
        assertNotNull(request);
        assertFalse(request.isStream());
        assertEquals("10m", request.getKeepAlive());
        assertNotNull(request.getOptions());
        assertEquals(4096, request.getOptions().getNumCtx());
        assertEquals(512, request.getOptions().getNumPredict());

        verify(performanceLogger).providerTelemetry(
                any(),
                eq("OLLAMA"),
                eq("llama3.1:8b"),
                eq(1),
                eq(24),
                eq(40),
                eq(2_500_000_000L),
                eq(100_000_000L),
                eq(30_000_000L),
                eq(2_000_000_000L));
    }

    @Test
    void streamsNdjsonDeltasAndMapsFinalTelemetry() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);
        ObjectMapper objectMapper = new ObjectMapper();

        OllamaConfig config = new OllamaConfig();
        config.setModel("llama3.2:3b");
        config.setNumCtx(4096);
        config.setNumPredict(512);
        config.setKeepAlive("10m");

        ProviderConcurrencyProperties concurrencyProperties =
                new ProviderConcurrencyProperties();
        concurrencyProperties.setProviders(new EnumMap<>(Provider.class));
        ProviderConcurrencyLimiter limiter = new ProviderConcurrencyLimiter(
                concurrencyProperties,
                performanceLogger);

        OllamaProvider provider = new OllamaProvider(
                restTemplate,
                config,
                performanceLogger,
                limiter,
                objectMapper);
        ReflectionTestUtils.setField(provider, "baseUrl", "http://localhost:11434");

        when(restTemplate.execute(
                anyString(),
                eq(HttpMethod.POST),
                any(RequestCallback.class),
                any(ResponseExtractor.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResponseExtractor<Object> extractor =
                            invocation.getArgument(3);
                    String body = """
                            {"message":{"role":"assistant","content":"hello "},"prompt_eval_count":4}
                            {"message":{"role":"assistant","content":"world"},"eval_count":2,"total_duration":5000000}
                            """;
                    ClientHttpResponse response = new MockClientHttpResponse(
                            body.getBytes(StandardCharsets.UTF_8),
                            HttpStatus.OK);
                    return extractor.extractData(response);
                });

        List<String> deltas = new java.util.ArrayList<>();
        AIStreamResult result = provider.stream(
                AIRequest.builder()
                        .provider(Provider.OLLAMA)
                        .model("llama3.2:3b")
                        .prompt("hello")
                        .build(),
                deltas::add);

        assertEquals(List.of("hello ", "world"), deltas);
        assertEquals("hello world", result.getResponse());
        assertEquals(Provider.OLLAMA, result.getProvider());
        assertEquals("llama3.2:3b", result.getModel());
        assertEquals(4, result.getInputTokens());
        assertEquals(2, result.getOutputTokens());
        assertEquals(6, result.getTotalTokens());

        verify(performanceLogger).providerCompleted(
                any(), eq("OLLAMA"), eq("llama3.2:3b"), eq(1), anyLong(), eq("HTTP_200"));
    }

    @Test
    void propagatesProviderTimeoutAndLogsFailure() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);
        ObjectMapper objectMapper = new ObjectMapper();

        OllamaConfig config = new OllamaConfig();
        config.setModel("llama3.2:3b");

        ProviderConcurrencyProperties concurrencyProperties =
                new ProviderConcurrencyProperties();
        concurrencyProperties.setProviders(new EnumMap<>(Provider.class));
        ProviderConcurrencyLimiter limiter = new ProviderConcurrencyLimiter(
                concurrencyProperties,
                performanceLogger);

        OllamaProvider provider = new OllamaProvider(
                restTemplate,
                config,
                performanceLogger,
                limiter,
                objectMapper);

        ResourceAccessException timeout = new ResourceAccessException(
                "Read timed out",
                new java.net.SocketTimeoutException("Read timed out"));
        when(restTemplate.execute(
                anyString(),
                eq(HttpMethod.POST),
                any(RequestCallback.class),
                any(ResponseExtractor.class)))
                .thenThrow(timeout);

        ResourceAccessException thrown = assertThrows(
                ResourceAccessException.class,
                () -> provider.stream(
                        AIRequest.builder()
                                .provider(Provider.OLLAMA)
                                .model("llama3.2:3b")
                                .prompt("hello")
                                .build(),
                        delta -> { }));

        assertSame(timeout, thrown);
        verify(performanceLogger).providerCompleted(
                any(), eq("OLLAMA"), eq("llama3.2:3b"), eq(1), anyLong(), eq("FAILED:ResourceAccessException"));
    }

}
