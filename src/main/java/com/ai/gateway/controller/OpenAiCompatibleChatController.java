package com.ai.gateway.controller;

import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.contract.ChatResponse;
import com.ai.gateway.core.contract.ContextMessage;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.service.GatewayService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class OpenAiCompatibleChatController {

    private final GatewayService gatewayService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/completions", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ResponseEntity<StreamingResponseBody> completions(
            @Valid @RequestBody OpenAiChatCompletionRequest request) {

        if (request.isStream()) {
            return streamResponse(request);
        }

        ChatResponse response = gatewayService.process(toCoreRequest(request));
        String content = response == null ? null : response.getResponse();
        UUID requestId = response == null ? null : response.getRequestId();

        OpenAiChatCompletionResponse result = OpenAiChatCompletionResponse.builder()
                .id("chatcmpl-" + (requestId == null ? UUID.randomUUID() : requestId))
                .object("chat.completion")
                .created(Instant.now().getEpochSecond())
                .model(request.getModel())
                .choices(List.of(OpenAiChoice.builder()
                        .index(0)
                        .message(OpenAiMessage.builder().role("assistant").content(content).build())
                        .finishReason("stop")
                        .build()))
                .build();

        StreamingResponseBody body = outputStream -> {
            outputStream.write(objectMapper.writeValueAsBytes(result));
            outputStream.flush();
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private ResponseEntity<StreamingResponseBody> streamResponse(
            OpenAiChatCompletionRequest request) {

        ChatRequest core = toCoreRequest(request);
        String completionId = "chatcmpl-" + UUID.randomUUID();
        long created = Instant.now().getEpochSecond();

        StreamingResponseBody body = outputStream -> gatewayService.stream(core, event -> {
            try {
                if ("delta".equals(event.getType()) || "replace".equals(event.getType())) {
                    OpenAiChatCompletionChunk chunk = OpenAiChatCompletionChunk.builder()
                            .id(completionId)
                            .object("chat.completion.chunk")
                            .created(created)
                            .model(request.getModel())
                            .choices(List.of(OpenAiChunkChoice.builder()
                                    .index(0)
                                    .delta(OpenAiDelta.builder().content(
                                            event.getContent() == null ? "" : event.getContent()).build())
                                    .build()))
                            .build();
                    writeSse(outputStream, chunk);
                } else if ("done".equals(event.getType())) {
                    OpenAiChatCompletionChunk chunk = OpenAiChatCompletionChunk.builder()
                            .id(completionId)
                            .object("chat.completion.chunk")
                            .created(created)
                            .model(request.getModel())
                            .choices(List.of(OpenAiChunkChoice.builder()
                                    .index(0)
                                    .delta(OpenAiDelta.builder().build())
                                    .finishReason("stop")
                                    .build()))
                            .build();
                    writeSse(outputStream, chunk);
                    outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } else if ("error".equals(event.getType())) {
                    OpenAiChatCompletionChunk chunk = OpenAiChatCompletionChunk.builder()
                            .id(completionId)
                            .object("chat.completion.chunk")
                            .created(created)
                            .model(request.getModel())
                            .choices(List.of(OpenAiChunkChoice.builder()
                                    .index(0)
                                    .delta(OpenAiDelta.builder().build())
                                    .finishReason("stop")
                                    .build()))
                            .build();
                    writeSse(outputStream, chunk);
                    outputStream.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private void writeSse(java.io.OutputStream outputStream, Object payload) throws java.io.IOException {
        outputStream.write(("data: " + objectMapper.writeValueAsString(payload) + "\n\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private ChatRequest toCoreRequest(OpenAiChatCompletionRequest request) {
        String prompt = request.getMessages().stream()
                .map(m -> m.getContent() == null ? "" : m.getContent())
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));

        java.util.List<ContextMessage> contextMessages = request.getMessages().stream()
                .map(m -> ContextMessage.builder()
                        .role(m.getRole())
                        .content(m.getContent() == null ? "" : m.getContent())
                        .build())
                .toList();

        return ChatRequest.builder()
                .prompt(prompt)
                .contextMessages(contextMessages)
                .provider(request.getProvider())
                .model(request.getModel())
                .billingMode(request.getBillingMode())
                .requiredCapabilities(request.getRequiredCapabilities() == null ? java.util.Set.of() : request.getRequiredCapabilities())
                .extensiveResearch(request.isExtensiveResearch())
                .executionRole(request.getExecutionRole())
                .routingPriority(request.getRoutingPriority())
                .routingOptimizationProfile(request.getRoutingOptimizationProfile())
                .routingSelectionMode(request.getRoutingSelectionMode())
                .routingTopN(request.getRoutingTopN())
                .routingEscalationProfile(request.getRoutingEscalationProfile())
                .maximumRequestCost(request.getMaximumRequestCost())
                .remainingWorkflowBudget(request.getRemainingWorkflowBudget())
                .build();
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiChatCompletionRequest {
        @NotBlank private String model;
        @NotEmpty @Valid private List<OpenAiMessage> messages;
        private Provider provider;
        private String billingMode;
        @Builder.Default private boolean stream = false;
        private java.util.Set<String> requiredCapabilities;
        private boolean extensiveResearch;
        private String executionRole;
        private String routingPriority;
        private String routingOptimizationProfile;
        @Builder.Default private String routingSelectionMode = "SINGLE";
        @Builder.Default private int routingTopN = 1;
        private String routingEscalationProfile;
        private java.math.BigDecimal maximumRequestCost;
        private java.math.BigDecimal remainingWorkflowBudget;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAiMessage {
        private String role;
        private String content;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenAiChatCompletionResponse {
        private String id; private String object; private long created; private String model;
        private List<OpenAiChoice> choices;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OpenAiChoice { private int index; private OpenAiMessage message; private String finishReason; }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenAiChatCompletionChunk {
        private String id; private String object; private long created; private String model;
        private List<OpenAiChunkChoice> choices;
    }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OpenAiChunkChoice { private int index; private OpenAiDelta delta; private String finishReason; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OpenAiDelta { private String role; private String content; }
}
