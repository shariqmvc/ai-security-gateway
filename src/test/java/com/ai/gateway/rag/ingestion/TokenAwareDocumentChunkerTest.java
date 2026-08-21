package com.ai.gateway.rag.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenAwareDocumentChunkerTest {

    @Test
    void shouldRespectConfiguredApproximateTokenBudgetAndProduceMetadata() {
        RagIngestionProperties properties = new RagIngestionProperties();
        properties.setChunkSizeTokens(10);
        properties.setChunkOverlapTokens(2);
        properties.setMaxChunks(20);

        TokenAwareDocumentChunker chunker =
                new TokenAwareDocumentChunker(properties, new ObjectMapper());

        List<DocumentChunk> chunks = chunker.chunk(
                "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen");

        assertTrue(chunks.size() > 1);
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
        assertTrue(chunks.get(0).tokenCount() <= 10);
        assertTrue(chunks.get(1).tokenCount() <= 10);
        assertTrue(chunks.get(0).metadataJson().contains("TOKEN_AWARE"));
    }

    @Test
    void shouldRejectInvalidOverlap() {
        RagIngestionProperties properties = new RagIngestionProperties();
        properties.setChunkSizeTokens(10);
        properties.setChunkOverlapTokens(10);

        TokenAwareDocumentChunker chunker =
                new TokenAwareDocumentChunker(properties, new ObjectMapper());

        assertThrows(
                IllegalArgumentException.class,
                () -> chunker.chunk("hello world"));
    }
}
