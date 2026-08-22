package com.ai.gateway.rag.embedding.openai;

import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderException;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.RagEmbeddingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    @Qualifier("openAiRestClient")
    private final RestClient restClient;
    private final OpenAIConfig config;
    private final RagEmbeddingProperties properties;

    @Override public String provider() { return "OPENAI"; }
    @Override public String defaultModel() { return properties.getOpenaiModel(); }

    @Override
    public List<EmbeddingVector> embed(List<String> texts, String model) {
        String selected = model == null || model.isBlank() ? defaultModel() : model;
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest(selected, texts);
        try {
            OpenAiEmbeddingResponse response = restClient.post()
                    .uri("https://api.openai.com/v1/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiEmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().size() != texts.size()) {
                throw new EmbeddingProviderException("OpenAI returned an invalid embedding response.");
            }
            return response.data().stream()
                    .sorted(java.util.Comparator.comparingInt(OpenAiEmbeddingData::index))
                    .map(data -> new EmbeddingVector(data.embedding().stream().map(Double::floatValue).toList()))
                    .toList();
        } catch (EmbeddingProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EmbeddingProviderException("OpenAI embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private record OpenAiEmbeddingRequest(String model, List<String> input) {}
    private record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {}
    private record OpenAiEmbeddingData(List<Double> embedding, int index) {}
}
