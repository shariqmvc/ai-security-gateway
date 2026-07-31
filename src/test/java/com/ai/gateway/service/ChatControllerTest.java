package com.ai.gateway.service;

import com.ai.gateway.controller.ChatController;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.service.GatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GatewayService gatewayService;

    @Test
    void shouldReturn200() throws Exception {

        when(gatewayService.process(any()))
                .thenReturn(
                        ChatResponse.builder()
                                .requestId(UUID.randomUUID())
                                .response("Hello from AI")
                                .build()
                );

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "prompt":"hello"
                        }
                        """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Response generated successfully."))
                .andExpect(jsonPath("$.data.response").value("Hello from AI"))
                .andExpect(jsonPath("$.data.requestId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}