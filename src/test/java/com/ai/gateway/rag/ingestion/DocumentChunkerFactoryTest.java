package com.ai.gateway.rag.ingestion;

import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DocumentChunkerFactoryTest {

    @Test
    void shouldResolveTokenAwareStrategy() {
        TokenAwareDocumentChunker token = mock(TokenAwareDocumentChunker.class);
        RecordAwareDocumentChunker record = mock(RecordAwareDocumentChunker.class);

        DocumentChunkerFactory factory =
                new DocumentChunkerFactory(token, record);

        assertSame(token, factory.resolve(ChunkingStrategy.TOKEN_AWARE));
    }

    @Test
    void shouldResolveRecordAwareStrategy() {
        TokenAwareDocumentChunker token = mock(TokenAwareDocumentChunker.class);
        RecordAwareDocumentChunker record = mock(RecordAwareDocumentChunker.class);

        DocumentChunkerFactory factory =
                new DocumentChunkerFactory(token, record);

        assertSame(record, factory.resolve(ChunkingStrategy.RECORD_AWARE));
    }

    @Test
    void shouldDefaultNullStrategyToTokenAware() {
        TokenAwareDocumentChunker token = mock(TokenAwareDocumentChunker.class);
        RecordAwareDocumentChunker record = mock(RecordAwareDocumentChunker.class);

        DocumentChunkerFactory factory =
                new DocumentChunkerFactory(token, record);

        assertSame(token, factory.resolve(null));
    }

    @Test
    void shouldRejectUnsupportedStrategy() {
        TokenAwareDocumentChunker token = mock(TokenAwareDocumentChunker.class);
        RecordAwareDocumentChunker record = mock(RecordAwareDocumentChunker.class);

        DocumentChunkerFactory factory =
                new DocumentChunkerFactory(token, record);

        assertThrows(
                DocumentIngestionException.class,
                () -> factory.resolve(ChunkingStrategy.SEMANTIC));
    }
}
