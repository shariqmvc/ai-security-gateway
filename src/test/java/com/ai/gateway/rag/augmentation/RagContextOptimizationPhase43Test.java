package com.ai.gateway.rag.augmentation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagContextOptimizationPhase43Test {

    private final RagContextOptimizer optimizer = new RagContextOptimizer();
    private final RagContextTokenEstimator estimator = new RagContextTokenEstimator();

    @Test
    void shouldRemoveExactDuplicateChunks() {
        UUID id = UUID.randomUUID();
        RagContextChunk first = chunk(id, "Tenant isolation is enforced per tenant.");
        RagContextChunk duplicate = chunk(UUID.randomUUID(), "Tenant isolation is enforced per tenant.");

        RagContextOptimizationResult result = optimizer.optimizeDetailed(
                List.of(first, duplicate), 1000, 10);

        assertEquals(2, result.getCandidateCount());
        assertEquals(1, result.getDeduplicatedCount());
        assertEquals(1, result.getSelectedChunks().size());
    }

    @Test
    void shouldRemoveNearDuplicateChunks() {
        RagContextChunk first = chunk(UUID.randomUUID(),
                "Tenant isolation protects retrieval by enforcing tenant scoped access controls.");
        RagContextChunk duplicate = chunk(UUID.randomUUID(),
                "Tenant isolation protects retrieval by enforcing tenant scoped access controls strongly.");

        RagContextOptimizationResult result = optimizer.optimizeDetailed(
                List.of(first, duplicate), 1000, 10);

        assertEquals(1, result.getSelectedChunks().size());
        assertEquals(1, result.getDeduplicatedCount());
    }

    @Test
    void shouldNeverExceedContextTokenBudget() {
        List<RagContextChunk> chunks = List.of(
                chunk(UUID.randomUUID(), "tenant isolation ".repeat(1000)),
                chunk(UUID.randomUUID(), "second chunk ".repeat(1000)));

        RagContextOptimizationResult result = optimizer.optimizeDetailed(chunks, 300, 10);

        assertTrue(result.getEstimatedContextTokens() <= result.getContextTokenBudget());
        assertFalse(result.getSelectedChunks().isEmpty());
        assertTrue(result.getTruncatedCount() >= 1 || result.getDroppedCount() >= 1);
    }

    @Test
    void shouldRespectTopKAfterDeduplication() {
        List<RagContextChunk> chunks = List.of(
                chunk(UUID.randomUUID(), "alpha unique evidence"),
                chunk(UUID.randomUUID(), "beta unique evidence"),
                chunk(UUID.randomUUID(), "gamma unique evidence"));

        RagContextOptimizationResult result = optimizer.optimizeDetailed(chunks, 1000, 2);

        assertEquals(2, result.getSelectedChunks().size());
    }

    @Test
    void shouldReturnDeterministicEstimatedContextTokens() {
        RagContextChunk chunk = chunk(UUID.randomUUID(), "deterministic context");
        int first = estimator.estimateContextTokens(List.of(chunk));
        int second = estimator.estimateContextTokens(List.of(chunk));
        assertEquals(first, second);
    }

    private RagContextChunk chunk(UUID id, String content) {
        return RagContextChunk.builder()
                .id(id)
                .documentId(UUID.randomUUID())
                .knowledgeBaseId(UUID.randomUUID())
                .fileName("policy.md")
                .content(content)
                .similarity(.9d)
                .build();
    }
}
