package com.ai.gateway.rag.ingestion;

public record DocumentChunk(
        int index,
        String content,
        int tokenCount,
        String metadataJson) {
}
