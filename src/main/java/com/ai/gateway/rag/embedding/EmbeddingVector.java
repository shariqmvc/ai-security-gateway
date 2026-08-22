package com.ai.gateway.rag.embedding;

import java.util.List;

public record EmbeddingVector(List<Float> values) {
    public EmbeddingVector {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Embedding vector must not be empty.");
        }
        values = List.copyOf(values);
    }

    public int dimension() {
        return values.size();
    }
}
