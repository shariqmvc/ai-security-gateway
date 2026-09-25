package com.ai.gateway.core.provider.gemini;

import com.ai.gateway.config.GeminiConfig;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.AIProvider;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.core.provider.StreamingAIProvider;
import com.ai.gateway.core.provider.gemini.dto.GeminiContent;
import com.ai.gateway.core.provider.gemini.dto.GeminiPart;
import com.ai.gateway.core.provider.gemini.dto.GeminiRequest;
import com.ai.gateway.core.provider.gemini.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import java.util.List;
import java.util.ArrayList;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.multimodal.MediaUrlFetcher;
import com.ai.gateway.core.multimodal.MediaInputException;

@Service
public class GeminiProvider implements AIProvider, StreamingAIProvider {

    public GeminiProvider(
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            GeminiConfig geminiConfig,
            PerformanceLogger performanceLogger,
            MediaUrlFetcher mediaUrlFetcher,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.geminiConfig = geminiConfig;
        this.performanceLogger = performanceLogger;
        this.mediaUrlFetcher = mediaUrlFetcher;
    }

    private final RestTemplate restTemplate;
    private final GeminiConfig geminiConfig;
    private final PerformanceLogger performanceLogger;
    private final MediaUrlFetcher mediaUrlFetcher;
    private final ObjectMapper objectMapper;

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
                                .parts(buildParts(request))
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

    @Override
    public AIStreamResult stream(AIRequest request, Consumer<String> deltaConsumer) {
        String requestIdValue = org.slf4j.MDC.get("requestId");
        java.util.UUID requestId = parseRequestId(requestIdValue);
        long started = System.nanoTime();

        String selectedModel = request.getModel() != null && !request.getModel().isBlank()
                ? request.getModel()
                : model;

        String url = baseUrl
                + "/v1beta/models/"
                + selectedModel
                + ":streamGenerateContent?alt=sse&key="
                + apiKey;

        GeminiRequest geminiRequest = GeminiRequest.builder()
                .contents(List.of(GeminiContent.builder().parts(buildParts(request)).build()))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));

        HttpEntity<GeminiRequest> entity = new HttpEntity<>(geminiRequest, headers);
        StringBuilder full = new StringBuilder();
        final int[] inputTokens = {0};
        final int[] outputTokens = {0};
        final int[] totalTokens = {0};

        try {
            performanceLogger.providerStart(
                    requestId, provider().name(), selectedModel, providerAttempt());

            restTemplate.execute(
                    url,
                    HttpMethod.POST,
                    outputStreamRequest -> {
                        outputStreamRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        outputStreamRequest.getHeaders().setAccept(
                                List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON));
                        outputStreamRequest.getBody().write(
                                objectMapper.writeValueAsBytes(geminiRequest));
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank() || !line.startsWith("data:")) {
                                    continue;
                                }

                                String json = line.substring("data:".length()).trim();
                                if (json.isEmpty() || "[DONE]".equals(json)) {
                                    continue;
                                }

                                GeminiResponse chunk = objectMapper.readValue(json, GeminiResponse.class);
                                if (chunk.getCandidates() != null && !chunk.getCandidates().isEmpty()
                                        && chunk.getCandidates().getFirst().getContent() != null
                                        && chunk.getCandidates().getFirst().getContent().getParts() != null) {
                                    for (GeminiPart part : chunk.getCandidates().getFirst().getContent().getParts()) {
                                        if (part.getText() != null && !part.getText().isEmpty()) {
                                            full.append(part.getText());
                                            deltaConsumer.accept(part.getText());
                                        }
                                    }
                                }

                                if (chunk.getUsageMetadata() != null) {
                                    if (chunk.getUsageMetadata().getPromptTokenCount() != null) {
                                        inputTokens[0] = chunk.getUsageMetadata().getPromptTokenCount();
                                    }
                                    if (chunk.getUsageMetadata().getCandidatesTokenCount() != null) {
                                        outputTokens[0] = chunk.getUsageMetadata().getCandidatesTokenCount();
                                    }
                                    if (chunk.getUsageMetadata().getTotalTokenCount() != null) {
                                        totalTokens[0] = chunk.getUsageMetadata().getTotalTokenCount();
                                    }
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

        if (full.isEmpty()) {
            throw new IllegalStateException("Gemini returned an empty streaming response.");
        }

        long latencyMs = elapsedMs(started);
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
                null,
                null,
                null,
                null);

        return AIStreamResult.builder()
                .response(full.toString())
                .provider(provider())
                .model(selectedModel)
                .inputTokens(inputTokens[0])
                .outputTokens(outputTokens[0])
                .totalTokens(totalTokens[0] == 0 ? inputTokens[0] + outputTokens[0] : totalTokens[0])
                .latencyMs(latencyMs)
                .build();
    }

    private List<GeminiPart> buildParts(AIRequest request) {
        List<GeminiPart> parts = new ArrayList<>();
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            parts.add(GeminiPart.builder().text(request.getPrompt()).build());
        }
        if (request.getMedia() != null) {
            for (MediaContent media : request.getMedia()) {
                if (media.getSourceType() == MediaSourceType.BASE64) {
                    parts.add(GeminiPart.builder()
                            .inlineData(GeminiPart.InlineData.builder()
                                    .mimeType(media.getMimeType())
                                    .data(media.getData())
                                    .build())
                            .build());
                } else if (media.getSourceType() == MediaSourceType.URL) {
                    MediaUrlFetcher.ResolvedMedia resolved = mediaUrlFetcher.fetch(media);
                    parts.add(GeminiPart.builder()
                            .inlineData(GeminiPart.InlineData.builder()
                                    .mimeType(resolved.mimeType())
                                    .data(resolved.base64Data())
                                    .build())
                            .build());
                } else {
                    throw new MediaInputException("Unsupported Gemini media source type.");
                }
            }
        }
        return parts;
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
