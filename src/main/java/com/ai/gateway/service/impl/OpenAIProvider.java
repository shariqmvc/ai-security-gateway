package com.ai.gateway.service.impl;

import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.dto.OpenAIRequest;
import com.ai.gateway.dto.OpenAIResponse;
import com.ai.gateway.service.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIProvider implements AiProvider {
    private final RestClient restClient;
    private final OpenAIConfig config;

    @Override
    public String chat(String prompt) {
        long start = System.currentTimeMillis();

        log.info(
                "Calling OpenAI. model={}",
                config.getModel());
        OpenAIRequest request = OpenAIRequest.builder()
                .model(config.getModel())
                .input(prompt)
                .build();

        OpenAIResponse response = restClient.post()
                .uri(config.getUrl())
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + config.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(OpenAIResponse.class);

        long latency = System.currentTimeMillis() - start;

        log.info(
                "OpenAI response received. latency={} ms",
                latency);

        if (response == null
                || response.getOutput() == null
                || response.getOutput().isEmpty()
                || response.getOutput().get(0).getContent().isEmpty()) {

            throw new RuntimeException("Empty response received from OpenAI.");
        }

        return response.getOutput()
                .get(0)
                .getContent()
                .get(0)
                .getText();

    }
}
