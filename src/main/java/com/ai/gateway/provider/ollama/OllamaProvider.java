package com.ai.gateway.provider.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.observability.PerformanceLogger;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.gemini.dto.GeminiContent;
import com.ai.gateway.provider.gemini.dto.GeminiPart;
import com.ai.gateway.provider.gemini.dto.GeminiRequest;
import com.ai.gateway.provider.gemini.dto.GeminiResponse;
import com.ai.gateway.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.provider.ollama.dto.OllamaResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OllamaProvider implements AIProvider {

    public OllamaProvider(
            @Qualifier("ollamaRestTemplate") RestTemplate restTemplate,
            OllamaConfig ollamaConfig,
            PerformanceLogger performanceLogger) {
        this.restTemplate = restTemplate;
        this.ollamaConfig = ollamaConfig;
        this.performanceLogger = performanceLogger;
    }

    private final RestTemplate restTemplate;
    private final OllamaConfig ollamaConfig;
    private final PerformanceLogger performanceLogger;

    @Value("${ollama.base.url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    @Override
    public Provider provider() {
        return Provider.OLLAMA;
    }

    @Override
    public String defaultModel() {
        return ollamaConfig.getModel();
    }

    @Override
    public AIResponse chat(AIRequest request) {

        java.util.UUID requestId = parseRequestId(org.slf4j.MDC.get("requestId"));
        long started = System.nanoTime();
        performanceLogger.providerStart(requestId, provider().name(), request.getModel(), providerAttempt());

        String selectedModel =
                request.getModel() != null && !request.getModel().isBlank()
                        ? request.getModel()
                        : model;

        String url = baseUrl + "/api/chat";

        OllamaRequest ollamaRequest =
                OllamaRequest.builder()
                        .model(selectedModel)
                        .messages(List.of(
                                OllamaMessage.builder()
                                        .role("user")
                                        .content(request.getPrompt())
                                        .build()))
                        .stream(false)
                        .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OllamaRequest> entity =
                new HttpEntity<>(ollamaRequest, headers);

        ResponseEntity<OllamaResponse> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    OllamaResponse.class);
        } catch (RuntimeException ex) {
            performanceLogger.providerCompleted(
                    requestId, provider().name(), request.getModel(), providerAttempt(),
                    elapsedMs(started), "FAILED:" + ex.getClass().getSimpleName());
            throw ex;
        }

        performanceLogger.providerCompleted(
                requestId, provider().name(), request.getModel(), providerAttempt(),
                elapsedMs(started), "HTTP_" + response.getStatusCode().value());

        String answer =
                response.getBody()
                        .getMessage()
                        .getContent();

        return AIResponse.builder()
                .response(answer)
                .usage(
                        Usage.builder()
                                .inputTokens(0)
                                .outputTokens(0)
                                .totalTokens(0)
                                .build()
                )
                .build();
    }

    private int providerAttempt() {
        String value = org.slf4j.MDC.get("providerAttempt");
        try {
            return value == null ? 1 : Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private java.util.UUID parseRequestId(String value) {
        if (value == null) return null;
        try { return java.util.UUID.fromString(value); }
        catch (IllegalArgumentException ex) { return null; }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}


