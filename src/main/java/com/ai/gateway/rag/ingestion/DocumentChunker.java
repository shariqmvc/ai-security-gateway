package com.ai.gateway.rag.ingestion;

import java.util.List;

public interface DocumentChunker {
    List<DocumentChunk> chunk(String normalizedText);
}
