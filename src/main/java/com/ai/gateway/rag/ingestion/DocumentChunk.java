package com.ai.gateway.rag.ingestion;

public record DocumentChunk(
        int index,
        String content,
        int tokenCount,
        String metadataJson,
        String recordId,
        String sectionId,
        String chunkId) {

    public DocumentChunk(
            int index,
            String content,
            int tokenCount,
            String metadataJson) {
        this(index, content, tokenCount, metadataJson, null, null, null);
    }
}
