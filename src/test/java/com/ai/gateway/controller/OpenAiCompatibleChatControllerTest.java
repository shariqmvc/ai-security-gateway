package com.ai.gateway.controller;

import com.ai.gateway.core.contract.ChatResponse;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.provider.AIStreamResult;
import com.ai.gateway.service.GatewayService;
import com.ai.gateway.service.StreamAdmission;
import com.ai.gateway.service.GatewayStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OpenAiCompatibleChatControllerTest {
    @Mock GatewayService gatewayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new OpenAiCompatibleChatController(gatewayService, new ObjectMapper())).build();
    }

    @Test
    void returnsOpenAiCompatibleCompletion() throws Exception {
        when(gatewayService.process(any())).thenReturn(ChatResponse.builder()
                .requestId(UUID.randomUUID())
                .response("AIRouter intelligently selects models and providers.")
                .build());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5\",\"messages\":[{\"role\":\"user\",\"content\":\"Explain AIRouter.\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content")
                        .value("AIRouter intelligently selects models and providers."));
    }

    @Test
    void rejectsMissingMessages() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(APPLICATION_JSON)
                        .content("{\"model\":\"gpt-5\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsOpenAiCompatibleSseForStreaming() throws Exception {
        when(gatewayService.preflightStream(any(), anyString(), anyString()))
                .thenReturn(new StreamAdmission(
                        UUID.randomUUID(), null, null, System.nanoTime()));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<GatewayStreamEvent> consumer = invocation.getArgument(2);
            consumer.accept(GatewayStreamEvent.builder().type("start").build());
            consumer.accept(GatewayStreamEvent.builder().type("delta").content("Hello").build());
            consumer.accept(GatewayStreamEvent.builder().type("done").build());
            return null;
        }).when(gatewayService).stream(any(), any(StreamAdmission.class), any());

        var result = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(APPLICATION_JSON)
                        .accept("text/event-stream")
                        .content("{\"model\":\"gpt-5\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chat.completion.chunk")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data: [DONE]")));
    }

    @Test
    void exposesRoutingMetadataAsDedicatedSseChunk() throws Exception {
        when(gatewayService.preflightStream(any(), anyString(), anyString()))
                .thenReturn(new StreamAdmission(
                        UUID.randomUUID(), null, null, System.nanoTime()));

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<GatewayStreamEvent> consumer = invocation.getArgument(2);
            consumer.accept(GatewayStreamEvent.builder().type("start").build());
            consumer.accept(GatewayStreamEvent.builder()
                    .type("routing")
                    .provider("OLLAMA")
                    .model("llama3.2:3b")
                    .endpointId("ollama-gpu-02")
                    .requestedProvider("OLLAMA")
                    .requestedModel("llama3.2:3b")
                    .failoverFromProvider("OLLAMA")
                    .failoverFromEndpointId("ollama-gpu-01")
                    .failoverReason("HTTP_429")
                    .providerAttempt(2)
                    .providerAttempts(List.of(
                            new AIStreamResult.ProviderAttempt(
                                    1, Provider.OLLAMA, "llama3.2:3b",
                                    "ollama-gpu-01", "FAILED", "HTTP_429"),
                            new AIStreamResult.ProviderAttempt(
                                    2, Provider.OLLAMA, "llama3.2:3b",
                                    "ollama-gpu-02", "SUCCESS", null)))
                    .phase("ROUTING_COMPLETE")
                    .build());
            consumer.accept(GatewayStreamEvent.builder().type("delta").content("Hello").build());
            consumer.accept(GatewayStreamEvent.builder().type("done").build());
            return null;
        }).when(gatewayService).stream(any(), any(StreamAdmission.class), any());

        var result = mockMvc.perform(post("/v1/chat/completions")
                        .contentType(APPLICATION_JSON)
                        .accept("text/event-stream")
                        .content("{\"model\":\"llama3.2:3b\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"object\":\"chat.completion.routing\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"endpointId\":\"ollama-gpu-02\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"failoverFromEndpointId\":\"ollama-gpu-01\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"providerAttempt\":2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"failureType\":\"HTTP_429\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data: [DONE]")));
    }
}
