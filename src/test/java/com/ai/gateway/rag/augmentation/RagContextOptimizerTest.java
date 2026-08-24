
package com.ai.gateway.rag.augmentation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagContextOptimizerTest {

    private final RagContextOptimizer optimizer = new RagContextOptimizer();

    @Test
    void shouldRespectChunkLimit() {
        List<RagContextChunk> chunks = List.of(
                chunk("a ".repeat(100)),
                chunk("b ".repeat(100)),
                chunk("c ".repeat(100)));

        List<RagContextChunk> selected = optimizer.optimize(chunks, 1000, 2);

        assertEquals(2, selected.size());
    }

    @Test
    void shouldRespectTokenBudgetAfterFirstChunk() {
        List<RagContextChunk> chunks = List.of(
                chunk("a ".repeat(1000)),
                chunk("b ".repeat(1000)));

        List<RagContextChunk> selected = optimizer.optimize(chunks, 300, 10);

        assertEquals(1, selected.size());
    }

    private RagContextChunk chunk(String content) {
        return RagContextChunk.builder()
                .id(UUID.randomUUID())
                .content(content)
                .similarity(.9)
                .build();
    }
}
