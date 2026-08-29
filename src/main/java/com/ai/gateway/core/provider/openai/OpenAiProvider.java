package com.ai.gateway.core.provider.openai;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.contract.Usage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.observability.PerformanceLogger;
import com.ai.gateway.core.provider.AIProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.core.provider.openai.dto.OpenAIRequest;
import com.ai.gateway.core.provider.openai.dto.OpenAIResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import com.ai.gateway.core.multimodal.MediaContent;
import com.ai.gateway.core.multimodal.MediaSourceType;
import com.ai.gateway.core.multimodal.MediaTypeKind;
import com.ai.gateway.core.provider.openai.dto.OpenAIInputAudio;
import com.ai.gateway.core.provider.openai.dto.OpenAIInputContent;
import com.ai.gateway.core.provider.openai.dto.OpenAIInputImage;
import com.ai.gateway.core.provider.openai.dto.OpenAIInputText;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class OpenAiProvider implements AIProvider {

    public OpenAiProvider(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAIConfig openAIConfig,
            PerformanceLogger performanceLogger) {
        this.restClient = restClient;
        this.openAIConfig = openAIConfig;
        this.performanceLogger = performanceLogger;
    }

    private final RestClient restClient;
    private final OpenAIConfig openAIConfig;
    private final PerformanceLogger performanceLogger;

    @Override
    public Provider provider() {
        return Provider.OPENAI;
    }

    @Override
    public String defaultModel() {
        return openAIConfig.getModel();
    }


    @Override
    public AIResponse chat(AIRequest request) {

        java.util.UUID requestId = parseRequestId(org.slf4j.MDC.get("requestId"));
        long started = System.nanoTime();
        performanceLogger.providerStart(requestId, provider().name(), request.getModel(), providerAttempt());
        log.info("Calling OpenAI. model={}", request.getModel());
        OpenAIRequest openAIRequest = OpenAIRequest.builder()
                .model(request.getModel())
                .input(buildInput(request))
                .build();

        OpenAIResponse response;
        try {
            response = restClient.post()
                    .uri(openAIConfig.getUrl())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + openAIConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(openAIRequest)
                    .retrieve()
                    .body(OpenAIResponse.class);
        } catch (RuntimeException ex) {
            performanceLogger.providerCompleted(
                    requestId, provider().name(), request.getModel(), providerAttempt(),
                    elapsedMs(started), "FAILED:" + ex.getClass().getSimpleName());
            throw ex;
        }
        performanceLogger.providerCompleted(
                requestId, provider().name(), request.getModel(), providerAttempt(),
                elapsedMs(started), "HTTP_200");

        if (response == null
                || response.getOutput() == null
                || response.getOutput().isEmpty()
                || response.getOutput().get(0).getContent() == null
                || response.getOutput().get(0).getContent().isEmpty()) {

            throw new RuntimeException("Empty response received from OpenAI");
        }

        String text = response.getOutput()
                .get(0)
                .getContent()
                .get(0)
                .getText();

        log.info("OpenAI response received successfully.");

        Usage usage = Usage.builder()
                .inputTokens(0)
                .outputTokens(0)
                .totalTokens(0)
                .build();

        if (response.getUsage() != null) {

            usage = Usage.builder()
                    .inputTokens(response.getUsage().getInputTokens())
                    .outputTokens(response.getUsage().getOutputTokens())
                    .totalTokens(response.getUsage().getTotalTokens())
                    .build();
        }

        return AIResponse.builder()
                .response(text)
                .providerRequestId(response.getId())
                .usage(usage)
                .build();
    }

    private Object buildInput(AIRequest request) {
        if (request.getMedia() == null || request.getMedia().isEmpty()) {
            return request.getPrompt();
        }
        List<Object> content = new ArrayList<>();
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            content.add(OpenAIInputText.builder().text(request.getPrompt()).build());
        }
        for (MediaContent media : request.getMedia()) {
            if (media.getType() == MediaTypeKind.IMAGE) {
                String imageUrl = media.getSourceType() == MediaSourceType.URL
                        ? media.getUrl()
                        : "data:" + media.getMimeType() + ";base64," + media.getData();
                content.add(OpenAIInputImage.builder()
                        .imageUrl(imageUrl)
                        .detail(media.getDetail())
                        .build());
            } else if (media.getType() == MediaTypeKind.AUDIO) {
                String format = media.getMimeType().substring(media.getMimeType().indexOf('/') + 1);
                String data = media.getData();
                if (data == null || data.isBlank()) {
                    throw new IllegalArgumentException("OpenAI audio input currently requires base64 data.");
                }
                content.add(OpenAIInputAudio.builder()
                        .inputAudio(OpenAIInputAudio.InputAudio.builder().data(data).format(format).build())
                        .build());
            }
        }
        return List.of(OpenAIInputContent.builder().role("user").content(content).build());
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