package com.ai.gateway.controller;

import com.ai.gateway.core.contract.ChatResponse;
import com.ai.gateway.service.GatewayService;
import com.ai.gateway.service.GatewayStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<GatewayStreamEvent> consumer = invocation.getArgument(1);
            consumer.accept(GatewayStreamEvent.builder().type("start").build());
            consumer.accept(GatewayStreamEvent.builder().type("delta").content("Hello").build());
            consumer.accept(GatewayStreamEvent.builder().type("done").build());
            return null;
        }).when(gatewayService).stream(any(), any());

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
}
