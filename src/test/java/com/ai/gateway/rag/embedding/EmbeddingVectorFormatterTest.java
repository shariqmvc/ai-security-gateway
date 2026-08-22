package com.ai.gateway.rag.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingVectorFormatterTest {
    @Test
    void shouldFormatVectorForPgvector() {
        assertEquals("[0.125,-0.5,1]",
                EmbeddingVectorFormatter.toPgVector(
                        new EmbeddingVector(List.of(0.125f, -0.5f, 1.0f))));
    }
}
