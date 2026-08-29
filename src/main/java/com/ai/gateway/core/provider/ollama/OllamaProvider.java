package com.ai.gateway.core.provider.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.config.ProviderConcurrencyLimiter;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.core.provider.StreamingAIProvider;
import com.ai.gateway.core.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.core.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.core.provider.ollama.dto.OllamaResponse;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.provider.ollama.dto.OllamaOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.InputStreamResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import java.util.List;

@Service
public class OllamaProvider implements AIProvider, StreamingAIProvider {

    private final RestTemplate restTemplate;
    private final OllamaConfig ollamaConfig;
    private final PerformanceLogger performanceLogger;
    private final ProviderConcurrencyLimiter concurrencyLimiter;
    private final ObjectMapper objectMapper;

    @Value("${ollama.base.url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    public OllamaProvider(
            @Qualifier("ollamaRestTemplate")
            RestTemplate restTemplate,
            OllamaConfig ollamaConfig,
            PerformanceLogger performanceLogger,
            ProviderConcurrencyLimiter concurrencyLimiter,
            ObjectMapper objectMapper) {

        this.restTemplate = restTemplate;
        this.ollamaConfig = ollamaConfig;
        this.performanceLogger = performanceLogger;
        this.concurrencyLimiter = concurrencyLimiter;
        this.objectMapper = objectMapper;
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
                        .messages(List.of(buildMessage(request)))
                        .stream(false)
                        .options(
                                OllamaOptions.builder()
                                        .numCtx(ollamaConfig.getNumCtx())
                                        .numPredict(ollamaConfig.getNumPredict())
                                        .build())
                        .keepAlive(ollamaConfig.getKeepAlive())
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

        } catch (RuntimeException ex) {

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

        OllamaResponse body = response.getBody();
        if (body == null || body.getMessage() == null) {
            throw new IllegalStateException("Ollama returned an empty response body.");
        }

        String answer = body.getMessage().getContent();

        Integer inputTokens = body.getPromptEvalCount();
        Integer outputTokens = body.getEvalCount();
        Integer totalTokens = null;
        if (inputTokens != null || outputTokens != null) {
            totalTokens = (inputTokens == null ? 0 : inputTokens)
                    + (outputTokens == null ? 0 : outputTokens);
        }

        performanceLogger.providerTelemetry(
                requestId,
                provider().name(),
                selectedModel,
                providerAttempt(),
                inputTokens,
                outputTokens,
                body.getTotalDuration(),
                body.getLoadDuration(),
                body.getPromptEvalDuration(),
                body.getEvalDuration());

        long latencyMs = elapsedMs(started);
        return AIResponse.builder()
                .response(answer)
                .provider(provider())
                .model(selectedModel)
                .usage(
                        Usage.builder()
                                .inputTokens(inputTokens == null ? 0 : inputTokens)
                                .outputTokens(outputTokens == null ? 0 : outputTokens)
                                .totalTokens(totalTokens == null ? 0 : totalTokens)
                                .latencyMs(latencyMs)
                                .build())
                .build();
    }


    @Override
    public AIStreamResult stream(
            AIRequest request,
            Consumer<String> deltaConsumer) {

        java.util.UUID requestId = parseRequestId(
                org.slf4j.MDC.get("requestId"));
        long started = System.nanoTime();

        String selectedModel =
                request.getModel() != null && !request.getModel().isBlank()
                        ? request.getModel()
                        : model;

        String url = baseUrl + "/api/chat";

        OllamaRequest ollamaRequest = OllamaRequest.builder()
                .model(selectedModel)
                .messages(List.of(buildMessage(request)))
                .stream(true)
                .options(OllamaOptions.builder()
                        .numCtx(ollamaConfig.getNumCtx())
                        .numPredict(ollamaConfig.getNumPredict())
                        .build())
                .keepAlive(ollamaConfig.getKeepAlive())
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.parseMediaType("application/x-ndjson"), MediaType.APPLICATION_JSON));

        HttpEntity<OllamaRequest> entity = new HttpEntity<>(ollamaRequest, headers);

        StringBuilder full = new StringBuilder();
        final int[] chunksSeen = {0};
        final int[] inputTokens = {0};
        final int[] outputTokens = {0};
        final long[] totalDuration = {0L};

        try (ProviderConcurrencyLimiter.Permit ignored =
                     concurrencyLimiter.acquire(requestId, provider())) {

            performanceLogger.providerStart(
                    requestId, provider().name(), selectedModel, providerAttempt());

            restTemplate.execute(
                    url,
                    HttpMethod.POST,
                    outputStreamRequest -> {
                        outputStreamRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        outputStreamRequest.getHeaders().setAccept(
                                List.of(MediaType.parseMediaType("application/x-ndjson"), MediaType.APPLICATION_JSON));
                        outputStreamRequest.getBody().write(
                                objectMapper.writeValueAsBytes(ollamaRequest));
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                        response.getBody(),
                                        StandardCharsets.UTF_8))) {

                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank()) {
                                    continue;
                                }

                                OllamaResponse chunk =
                                        objectMapper.readValue(line, OllamaResponse.class);
                                chunksSeen[0]++;

                                if (chunk.getMessage() != null
                                        && chunk.getMessage().getContent() != null
                                        && !chunk.getMessage().getContent().isEmpty()) {

                                    String delta = chunk.getMessage().getContent();
                                    full.append(delta);
                                    deltaConsumer.accept(delta);
                                }

                                if (chunk.getPromptEvalCount() != null) {
                                    inputTokens[0] = chunk.getPromptEvalCount();
                                }
                                if (chunk.getEvalCount() != null) {
                                    outputTokens[0] = chunk.getEvalCount();
                                }
                                if (chunk.getTotalDuration() != null) {
                                    totalDuration[0] = chunk.getTotalDuration();
                                }
                            }
                        }
                        return null;
                    });

        } catch (RuntimeException ex) {
            performanceLogger.providerCompleted(
                    requestId,
                    provider().name(),
                    selectedModel,
                    providerAttempt(),
                    elapsedMs(started),
                    "FAILED:" + ex.getClass().getSimpleName());
            throw ex;
        }

        if (chunksSeen[0] == 0 || full.isEmpty()) {
            throw new IllegalStateException(
                    "Ollama returned an empty streaming response.");
        }

        long latencyMs = elapsedMs(started);
        int totalTokens = inputTokens[0] + outputTokens[0];

        performanceLogger.providerCompleted(
                requestId,
                provider().name(),
                selectedModel,
                providerAttempt(),
                latencyMs,
                "HTTP_200");

        performanceLogger.providerTelemetry(
                requestId,
                provider().name(),
                selectedModel,
                providerAttempt(),
                inputTokens[0],
                outputTokens[0],
                totalDuration[0] == 0L ? null : totalDuration[0],
                null,
                null,
                null);

        return AIStreamResult.builder()
                .response(full.toString())
                .provider(provider())
                .model(selectedModel)
                .inputTokens(inputTokens[0])
                .outputTokens(outputTokens[0])
                .totalTokens(totalTokens)
                .latencyMs(latencyMs)
                .build();
    }

    private OllamaMessage buildMessage(AIRequest request) {
        List<String> images = new java.util.ArrayList<>();
        if (request.getMedia() != null) {
            for (MediaContent media : request.getMedia()) {
                if (media.getType() != MediaTypeKind.IMAGE) {
                    throw new IllegalArgumentException("Ollama provider currently supports IMAGE media only.");
                }
                if (media.getSourceType() != MediaSourceType.BASE64) {
                    throw new IllegalArgumentException("Ollama image input currently requires BASE64 data.");
                }
                images.add(media.getData());
            }
        }
        return OllamaMessage.builder()
                .role("user")
                .content(request.getPrompt())
                .images(images.isEmpty() ? null : images)
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