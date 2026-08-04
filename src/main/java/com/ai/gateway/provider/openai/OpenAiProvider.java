package com.ai.gateway.provider.openai;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.dto.Usage;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provider.AIProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.provider.openai.dto.OpenAIRequest;
import com.ai.gateway.provider.openai.dto.OpenAIResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiProvider implements AIProvider {

    private final RestClient restClient;
    private final OpenAIConfig openAIConfig;

    @Override
    public Provider provider() {
        return Provider.OPENAI;
    }


    @Override
    public AIResponse chat(AIRequest request) {

        log.info("Calling OpenAI. model={}", request.getModel());

        OpenAIRequest openAIRequest = OpenAIRequest.builder()
                .model(request.getModel())
                .input(request.getPrompt())
                .build();

        OpenAIResponse response = restClient.post()
                .uri(openAIConfig.getUrl())
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + openAIConfig.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAIRequest)
                .retrieve()
                .body(OpenAIResponse.class);

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
}