package com.ai.gateway.provider.gemini;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import com.ai.gateway.provider.gemini.dto.GeminiContent;
import com.ai.gateway.provider.gemini.dto.GeminiPart;
import com.ai.gateway.provider.gemini.dto.GeminiRequest;
import com.ai.gateway.provider.gemini.dto.GeminiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeminiProvider implements AIProvider {

    private final RestTemplate restTemplate;

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
    public AIResponse chat(AIRequest request) {

        String url = baseUrl +
                "/v1beta/models/" +
                model +
                ":generateContent?key=" +
                apiKey;

        GeminiRequest geminiRequest = GeminiRequest.builder()
                .contents(List.of(
                        GeminiContent.builder()
                                .parts(List.of(
                                        GeminiPart.builder()
                                                .text(request.getPrompt())
                                                .build()))
                                .build()))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequest> entity =
                new HttpEntity<>(geminiRequest, headers);

        ResponseEntity<GeminiResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        entity,
                        GeminiResponse.class);

        String answer =
                response.getBody()
                        .getCandidates()
                        .getFirst()
                        .getContent()
                        .getParts()
                        .getFirst()
                        .getText();

        return AIResponse.builder()
                .response(answer)
                .build();
    }
}
