package com.ai.gateway.rag.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingProviderFactoryTest {
    private final EmbeddingProviderFactory factory = new EmbeddingProviderFactory(List.of(
            new StubProvider("OLLAMA"),
            new StubProvider("OPENAI")));

    @Test
    void shouldResolveProviderCaseInsensitively() {
        assertEquals("OLLAMA", factory.get("ollama").provider());
    }

    @Test
    void shouldRejectUnknownProvider() {
        assertThrows(EmbeddingProviderException.class, () -> factory.get("CLAUDE"));
    }

    private record StubProvider(String provider) implements EmbeddingProvider {
        @Override public String defaultModel() { return "test-model"; }
        @Override public List<EmbeddingVector> embed(List<String> texts, String model) { return List.of(); }
    }
}
