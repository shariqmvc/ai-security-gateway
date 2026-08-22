package com.ai.gateway.rag.embedding.ollama;

import com.ai.gateway.config.OllamaConfig;
import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderException;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.RagEmbeddingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class OllamaEmbeddingProvider implements EmbeddingProvider {
    private final RestTemplate restTemplate;
    private final OllamaConfig config;
    private final RagEmbeddingProperties properties;

    public OllamaEmbeddingProvider(
            @Qualifier("ollamaRestTemplate") RestTemplate restTemplate,
            OllamaConfig config,
            RagEmbeddingProperties properties) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.properties = properties;
    }

    @Override public String provider() { return "OLLAMA"; }
    @Override public String defaultModel() { return properties.getOllamaModel(); }

    @Override
    public List<EmbeddingVector> embed(List<String> texts, String model) {
        String selected = model == null || model.isBlank() ? defaultModel() : model;
        OllamaEmbedRequest request = new OllamaEmbedRequest(selected, texts);
        try {
            OllamaEmbedResponse response = restTemplate.postForObject(
                    config.getBase().getUrl() + "/api/embed", request, OllamaEmbedResponse.class);
            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                throw new EmbeddingProviderException("Ollama returned an invalid embedding response.");
            }
            return response.embeddings().stream().map(values -> new EmbeddingVector(values.stream().map(Double::floatValue).toList())).toList();
        } catch (EmbeddingProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EmbeddingProviderException("Ollama embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private record OllamaEmbedRequest(String model, List<String> input) {}
    private record OllamaEmbedResponse(List<List<Double>> embeddings) {}
}
