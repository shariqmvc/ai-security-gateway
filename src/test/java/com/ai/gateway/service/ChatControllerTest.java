package com.ai.gateway.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.authentication.AuthenticationResult;
import com.ai.gateway.authentication.AuthenticationService;
import com.ai.gateway.authentication.AuthenticationType;
import com.ai.gateway.controller.ChatController;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.quota.service.QuotaService;
import com.ai.gateway.ratelimit.dto.RateLimitResult;
import com.ai.gateway.ratelimit.service.RateLimiterService;
import com.ai.gateway.service.GatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private RateLimiterService rateLimiterService;
    @MockitoBean
    private QuotaService quotaService;


    @Test
    void shouldReturn200() throws Exception {

        /*
         * ---------------------------------------------------------
         * Authentication
         * ---------------------------------------------------------
         */

        AuthenticationContext context =
                AuthenticationContext.builder()
                        .authenticationType(
                                AuthenticationType.API_KEY)
                        .apiKeyId(UUID.randomUUID())
                        .clientName("test-client")
                        .tenantId(UUID.randomUUID())
                        .tenantCode("TEST")
                        .tenantName("Test Tenant")
                        .tenantType(null)
                        .defaultProvider(Provider.GEMINI)
                        .defaultModel("gemini-test")
                        .schemaName("tenant_test")
                        .build();

        AuthenticationResult authenticationResult =
                mock(AuthenticationResult.class);

        when(authenticationResult.isAuthenticated())
                .thenReturn(true);

        when(authenticationResult.getMessage())
                .thenReturn("Authenticated");

        when(authenticationResult.getContext())
                .thenReturn(context);

        when(authenticationService.authenticate(any()))
                .thenReturn(authenticationResult);


        /*
         * ---------------------------------------------------------
         * Rate limiting
         * ---------------------------------------------------------
         */

        RateLimitResult rateLimitResult =
                mock(RateLimitResult.class);

        when(rateLimitResult.isAllowed())
                .thenReturn(true);

        when(rateLimiterService.check(any()))
                .thenReturn(rateLimitResult);


        /*
         * ---------------------------------------------------------
         * Gateway response
         * ---------------------------------------------------------
         */

        when(gatewayService.process(any()))
                .thenReturn(
                        ChatResponse.builder()
                                .requestId(UUID.randomUUID())
                                .response("Hello from AI")
                                .build()
                );


        /*
         * ---------------------------------------------------------
         * HTTP request
         * ---------------------------------------------------------
         */

        mockMvc.perform(
                        post("/api/chat")
                                .with(csrf())
                                .with(user("test-user"))
                                .contentType(
                                        MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "prompt":"hello"
                                    }
                                    """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .contentTypeCompatibleWith(
                                        MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath("$.success")
                                .value(true))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Response generated successfully."))
                .andExpect(
                        jsonPath("$.data.response")
                                .value("Hello from AI"))
                .andExpect(
                        jsonPath("$.data.requestId")
                                .exists())
                .andExpect(
                        jsonPath("$.timestamp")
                                .exists());
    }
}