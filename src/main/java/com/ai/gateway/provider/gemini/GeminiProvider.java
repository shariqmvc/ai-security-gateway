package com.ai.gateway.provider.gemini;

import com.ai.gateway.config.GeminiConfig;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class GeminiProvider implements AIProvider {

    public GeminiProvider(
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            GeminiConfig geminiConfig,
            PerformanceLogger performanceLogger) {
        this.restTemplate = restTemplate;
        this.geminiConfig = geminiConfig;
        this.performanceLogger = performanceLogger;
    }

    private final RestTemplate restTemplate;
    private final GeminiConfig geminiConfig;
    private final PerformanceLogger performanceLogger;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.base.url}")
    private String baseUrl;

    @Value("${gemini.model}")
    private String model;

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public String defaultModel() {
        return geminiConfig.getModel();
    }

    @Override
    public AIResponse chat(AIRequest request) {

        String requestIdValue = org.slf4j.MDC.get("requestId");
        java.util.UUID requestId = parseRequestId(requestIdValue);
        long started = System.nanoTime();
        performanceLogger.providerStart(
                requestId,
                provider().name(),
                request.getModel(),
                providerAttempt());

        String selectedModel =
                request.getModel() != null && !request.getModel().isBlank()
                        ? request.getModel()
                        : model;

        String url = baseUrl +
                "/v1beta/models/" +
                selectedModel +
                ":generateContent?key=" +
                apiKey;

        GeminiRequest geminiRequest = GeminiRequest.builder()
                .contents(List.of(
                        GeminiContent.builder()
                                .parts(List.of(
                                        GeminiPart.builder()
                                                .text(request.getPrompt())
                                                .build()))
                                .build()))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequest> entity =
                new HttpEntity<>(geminiRequest, headers);

        ResponseEntity<GeminiResponse> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    GeminiResponse.class);
        } catch (RuntimeException ex) {
            performanceLogger.providerCompleted(
                    requestId,
                    provider().name(),
                    request.getModel(),
                    providerAttempt(),
                    elapsedMs(started),
                    "FAILED:" + ex.getClass().getSimpleName());
            throw ex;
        }

        performanceLogger.providerCompleted(
                requestId,
                provider().name(),
                request.getModel(),
                providerAttempt(),
                elapsedMs(started),
                "HTTP_" + response.getStatusCode().value());

        String answer =
                response.getBody()
                        .getCandidates()
                        .getFirst()
                        .getContent()
                        .getParts()
                        .getFirst()
                        .getText();

        Usage usage =
                Usage.builder()
                        .inputTokens(
                                response.getBody().getUsageMetadata()
                                        .getPromptTokenCount())

                        .outputTokens(
                                response.getBody().getUsageMetadata()
                                        .getCandidatesTokenCount())

                        .totalTokens(
                                response.getBody().getUsageMetadata()
                                        .getTotalTokenCount())
                        .reasoningTokens( response.getBody().getUsageMetadata().getThoughtsTokenCount())

                        .build();

        return AIResponse.builder()
                .response(answer)
                .usage(
                        usage
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
        if (value == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
