package com.ai.gateway.rag.ingestion;

import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentChunkerFactory {

    private final TokenAwareDocumentChunker tokenAwareDocumentChunker;
    private final RecordAwareDocumentChunker recordAwareDocumentChunker;

    public DocumentChunker resolve(ChunkingStrategy strategy) {
        ChunkingStrategy effective = strategy == null
                ? ChunkingStrategy.TOKEN_AWARE
                : strategy;

        return switch (effective) {
            case TOKEN_AWARE -> tokenAwareDocumentChunker;
            case RECORD_AWARE -> recordAwareDocumentChunker;
            default -> throw new DocumentIngestionException(
                    "RAG chunking strategy is not implemented: " + effective);
        };
    }
}
