package com.ai.gateway.provider.ollama;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.gemini.dto.GeminiContent;
import com.ai.gateway.provider.gemini.dto.GeminiPart;
import com.ai.gateway.provider.gemini.dto.GeminiRequest;
import com.ai.gateway.provider.gemini.dto.GeminiResponse;
import com.ai.gateway.provider.ollama.dto.OllamaMessage;
import com.ai.gateway.provider.ollama.dto.OllamaRequest;
import com.ai.gateway.provider.ollama.dto.OllamaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OllamaProvider implements AIProvider {

    private final RestTemplate restTemplate;

    @Value("${ollama.base.url}")
    private String baseUrl;

    @Value("${ollama.model}")
    private String model;

    @Override
    public Provider provider() {
        return Provider.OLLAMA;
    }

    @Override
    public AIResponse chat(AIRequest request) {

        String url = baseUrl + "/api/chat";

        OllamaRequest ollamaRequest =
                OllamaRequest.builder()
                        .model(model)
                        .messages(List.of(
                                OllamaMessage.builder()
                                        .role("user")
                                        .content(request.getPrompt())
                                        .build()))
                        .stream(false)
                        .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<OllamaRequest> entity =
                new HttpEntity<>(ollamaRequest, headers);

        ResponseEntity<OllamaResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        OllamaResponse.class);

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
    }


