package com.ai.gateway.provider.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.config.ProviderConcurrencyLimiter;
import com.ai.gateway.config.ProviderConcurrencyProperties;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.observability.PerformanceLogger;
import com.ai.gateway.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.provider.ollama.dto.OllamaOptions;
import com.ai.gateway.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.provider.ollama.dto.OllamaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OllamaProviderTest {

    @Test
    void sendsLatencyControlsAndMapsOllamaTelemetry() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        PerformanceLogger performanceLogger = mock(PerformanceLogger.class);

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
                limiter);

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
}
