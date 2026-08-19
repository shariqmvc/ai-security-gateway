package com.ai.gateway.provider.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.config.ProviderConcurrencyLimiter;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.observability.PerformanceLogger;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.provider.ollama.dto.OllamaResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OllamaProvider implements AIProvider {

    private final RestTemplate restTemplate;
    private final OllamaConfig ollamaConfig;
    private final PerformanceLogger performanceLogger;
    private final ProviderConcurrencyLimiter concurrencyLimiter;

    @Value("${ollama.base.url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    public OllamaProvider(
            @Qualifier("ollamaRestTemplate")
            RestTemplate restTemplate,
            OllamaConfig ollamaConfig,
            PerformanceLogger performanceLogger,
            ProviderConcurrencyLimiter concurrencyLimiter) {

        this.restTemplate = restTemplate;
        this.ollamaConfig = ollamaConfig;
        this.performanceLogger = performanceLogger;
        this.concurrencyLimiter = concurrencyLimiter;
    }

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

        java.util.UUID requestId =
                parseRequestId(
                        org.slf4j.MDC.get("requestId")
                );

        /*
         * Keep this timestamp BEFORE semaphore acquisition.
         *
         * Therefore providerCompleted latency continues to represent
         * the complete provider stage, including any concurrency wait.
         */
        long started =
                System.nanoTime();

        String selectedModel =
                request.getModel() != null
                        && !request.getModel().isBlank()
                        ? request.getModel()
                        : model;

        String url =
                baseUrl + "/api/chat";

        OllamaRequest ollamaRequest =
                OllamaRequest.builder()
                        .model(selectedModel)
                        .messages(
                                List.of(
                                        OllamaMessage.builder()
                                                .role("user")
                                                .content(
                                                        request.getPrompt()
                                                )
                                                .build()
                                )
                        )
                        .stream(false)
                        .build();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        HttpEntity<OllamaRequest> entity =
                new HttpEntity<>(
                        ollamaRequest,
                        headers
                );

        ResponseEntity<OllamaResponse> response;

        /*
         * IMPORTANT:
         *
         * The permit is acquired BEFORE PROVIDER_REQUEST_START.
         *
         * Therefore PROVIDER_REQUEST_START now means:
         *
         * "This request has obtained downstream provider capacity
         *  and is about to execute against Ollama."
         */
        try (
                ProviderConcurrencyLimiter.Permit ignored =
                        concurrencyLimiter.acquire(
                                requestId,
                                provider()
                        )
        ) {

            performanceLogger.providerStart(
                    requestId,
                    provider().name(),
                    request.getModel(),
                    providerAttempt()
            );

            response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    OllamaResponse.class
            );

            performanceLogger.providerCompleted(
                    requestId,
                    provider().name(),
                    request.getModel(),
                    providerAttempt(),
                    elapsedMs(started),
                    "HTTP_" + response.getStatusCode().value()
            );
        }catch (RuntimeException ex) {

            performanceLogger.providerCompleted(
                    requestId,
                    provider().name(),
                    request.getModel(),
                    providerAttempt(),
                    elapsedMs(started),
                    "FAILED:"
                            + ex.getClass()
                            .getSimpleName()
            );

            throw ex;
        }

        performanceLogger.providerCompleted(
                requestId,
                provider().name(),
                request.getModel(),
                providerAttempt(),
                elapsedMs(started),
                "HTTP_"
                        + response.getStatusCode()
                        .value()
        );

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

        String value =
                org.slf4j.MDC.get(
                        "providerAttempt"
                );

        try {

            return value == null
                    ? 1
                    : Math.max(
                    1,
                    Integer.parseInt(value)
            );

        } catch (NumberFormatException ex) {

            return 1;
        }
    }

    private java.util.UUID parseRequestId(
            String value) {

        if (value == null) {
            return null;
        }

        try {

            return java.util.UUID.fromString(
                    value
            );

        } catch (IllegalArgumentException ex) {

            return null;
        }
    }

    private long elapsedMs(
            long startNanos) {

        return (
                System.nanoTime()
                        - startNanos
        ) / 1_000_000L;
    }
}