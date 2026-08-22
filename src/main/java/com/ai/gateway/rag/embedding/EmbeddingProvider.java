package com.ai.gateway.rag.embedding;

import java.util.List;

/** Provider-neutral contract for generating embeddings for RAG chunks. */
public interface EmbeddingProvider {
    String provider();
    String defaultModel();
    List<EmbeddingVector> embed(List<String> texts, String model);
}
