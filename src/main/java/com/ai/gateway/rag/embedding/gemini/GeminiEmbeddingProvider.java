package com.ai.gateway.rag.embedding.gemini;

import com.ai.gateway.config.GeminiConfig;
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
public class GeminiEmbeddingProvider implements EmbeddingProvider {
    private final RestTemplate restTemplate;
    private final GeminiConfig config;
    private final RagEmbeddingProperties properties;

    public GeminiEmbeddingProvider(
            @Qualifier("geminiRestTemplate") RestTemplate restTemplate,
            GeminiConfig config,
            RagEmbeddingProperties properties) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.properties = properties;
    }

    @Override public String provider() { return "GEMINI"; }
    @Override public String defaultModel() { return properties.getGeminiModel(); }

    @Override
    public List<EmbeddingVector> embed(List<String> texts, String model) {
        String selected = model == null || model.isBlank() ? defaultModel() : model;
        return texts.stream().map(text -> embedOne(selected, text)).toList();
    }

    private EmbeddingVector embedOne(String model, String text) {
        GeminiEmbedRequest request = new GeminiEmbedRequest(
                new GeminiContent(List.of(new GeminiPart(text))));
        String url = config.getUrl() != null && !config.getUrl().isBlank()
                ? config.getUrl()
                : "https://generativelanguage.googleapis.com";
        try {
            GeminiEmbedResponse response = restTemplate.postForObject(
                    url + "/v1beta/models/" + model + ":embedContent?key=" + config.getApiKey(),
                    request,
                    GeminiEmbedResponse.class);
            if (response == null || response.embedding() == null || response.embedding().values() == null) {
                throw new EmbeddingProviderException("Gemini returned an invalid embedding response.");
            }
            return new EmbeddingVector(response.embedding().values().stream().map(Double::floatValue).toList());
        } catch (EmbeddingProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new EmbeddingProviderException("Gemini embedding request failed: " + ex.getMessage(), ex);
        }
    }

    private record GeminiEmbedRequest(GeminiContent content) {}
    private record GeminiContent(List<GeminiPart> parts) {}
    private record GeminiPart(String text) {}
    private record GeminiEmbedResponse(GeminiEmbedding embedding) {}
    private record GeminiEmbedding(List<Double> values) {}
}
