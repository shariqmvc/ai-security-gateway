package com.ai.gateway.rag.augmentation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagContextAssemblerTest {

    private final RagContextAssembler assembler = new RagContextAssembler();

    @Test
    void shouldReturnOriginalPromptWhenNoContextExists() {
        assertEquals("hello", assembler.augment("hello", List.of()));
    }

    @Test
    void shouldAssembleUntrustedSourceContext() {
        RagContextChunk chunk = RagContextChunk.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .knowledgeBaseId(UUID.randomUUID())
                .fileName("policy.md")
                .chunkIndex(0)
                .content("Cost guardrails apply.")
                .similarity(0.81234567d)
                .build();

        String result = assembler.augment("What is the policy?", List.of(chunk));

        assertTrue(result.startsWith("What is the policy?\n\nRETRIEVED KNOWLEDGE:"));
        assertTrue(result.contains("untrusted reference material"));
        assertTrue(result.contains("<source file=\"policy.md\" similarity=\"0.812346\">") );
        assertTrue(result.contains("Cost guardrails apply."));
        assertTrue(result.contains("</source>"));
    }

    @Test
    void shouldEscapeSourceFileAttribute() {
        RagContextChunk chunk = RagContextChunk.builder()
                .fileName("policy\"<x>.md")
                .content("content")
                .similarity(0.8d)
                .build();

        String result = assembler.assemble(List.of(chunk));

        assertTrue(result.contains("policy&quot;&lt;x&gt;.md"));
    }
}
