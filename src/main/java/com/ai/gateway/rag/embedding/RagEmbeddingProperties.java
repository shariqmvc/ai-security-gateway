package com.ai.gateway.rag.embedding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag.embedding")
@Getter
@Setter
public class RagEmbeddingProperties {
    private boolean enabled = true;
    private int batchSize = 8;
    private String defaultProvider = "OLLAMA";
    private String ollamaModel = "nomic-embed-text";
    private String ollamaKeepAlive = "2m";
    private String openaiModel = "text-embedding-3-small";
    private String geminiModel = "gemini-embedding-001";
}
