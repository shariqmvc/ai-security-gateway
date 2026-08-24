package com.ai.gateway.rag.augmentation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagContextChunkBuilderTest {

    @Test
    void shouldCopyChunkAndReplaceContent() {
        UUID id = UUID.randomUUID();
        RagContextChunk original = RagContextChunk.builder()
                .id(id)
                .content("original content")
                .similarity(0.9d)
                .build();

        RagContextChunk truncated = original.toBuilder()
                .content("truncated")
                .build();

        assertEquals(id, truncated.getId());
        assertEquals("truncated", truncated.getContent());
        assertEquals(0.9d, truncated.getSimilarity());
    }
}
