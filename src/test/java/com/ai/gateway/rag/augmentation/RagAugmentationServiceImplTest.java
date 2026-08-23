package com.ai.gateway.rag.augmentation;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.rag.search.RagSearchService;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagAugmentationServiceImplTest {

    private final RagSearchService ragSearchService = mock(RagSearchService.class);
    private final RagContextAssembler contextAssembler = new RagContextAssembler();
    private final RagAugmentationServiceImpl service =
            new RagAugmentationServiceImpl(ragSearchService, contextAssembler);

    @Test
    void shouldReturnOriginalPromptWhenRagIsDisabled() {
        RagAugmentationResult result = service.augment(
                UUID.randomUUID(),
                "hello",
                RagRequest.builder().enabled(false).build());

        assertEquals("hello", result.getAugmentedPrompt());
        assertTrue(result.getChunks().isEmpty());
        verifyNoInteractions(ragSearchService);
    }

    @Test
    void shouldRetrieveFromMultipleKnowledgeBasesAndGloballyRankResults() {
        UUID tenantId = UUID.randomUUID();
        UUID kb1 = UUID.randomUUID();
        UUID kb2 = UUID.randomUUID();

        RagSearchResult lower = result("lower.md", 0, 0.81d);
        RagSearchResult higher = result("higher.md", 0, 0.91d);

        when(ragSearchService.search(eq(tenantId), eq(kb1), any(RagSearchRequest.class)))
                .thenReturn(response(kb1, List.of(lower)));
        when(ragSearchService.search(eq(tenantId), eq(kb2), any(RagSearchRequest.class)))
                .thenReturn(response(kb2, List.of(higher)));

        RagRequest request = RagRequest.builder()
                .enabled(true)
                .knowledgeBaseIds(List.of(kb1.toString(), kb2.toString()))
                .topK(1)
                .minScore(0.70d)
                .retrievalStrategy("VECTOR")
                .build();

        RagAugmentationResult result = service.augment(
                tenantId,
                "How does it work?",
                request);

        assertEquals(2, result.getRetrievedCount());
        assertEquals(1, result.getSelectedCount());
        assertEquals("higher.md", result.getChunks().getFirst().getFileName());
        assertTrue(result.getAugmentedPrompt().contains("higher.md"));
        assertFalse(result.getAugmentedPrompt().contains("lower.md"));

        verify(ragSearchService).search(
                eq(tenantId), eq(kb1), argThat(r ->
                        r.getQuery().equals("How does it work?")
                                && r.getTopK() == 1
                                && r.getMinScore() == 0.70d));
        verify(ragSearchService).search(
                eq(tenantId), eq(kb2), any(RagSearchRequest.class));
    }

    @Test
    void shouldRejectUnsupportedRetrievalStrategy() {
        RagRequest request = RagRequest.builder()
                .enabled(true)
                .knowledgeBaseIds(List.of(UUID.randomUUID().toString()))
                .retrievalStrategy("HYBRID")
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.augment(UUID.randomUUID(), "hello", request));

        assertTrue(ex.getMessage().contains("Phase 4 currently supports VECTOR only"));
        verifyNoInteractions(ragSearchService);
    }

    @Test
    void shouldRejectMissingKnowledgeBases() {
        RagRequest request = RagRequest.builder()
                .enabled(true)
                .knowledgeBaseIds(List.of())
                .retrievalStrategy("VECTOR")
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.augment(UUID.randomUUID(), "hello", request));

        assertEquals(
                "At least one knowledge base is required when RAG is enabled.",
                ex.getMessage());
    }

    @Test
    void shouldRejectInvalidKnowledgeBaseId() {
        RagRequest request = RagRequest.builder()
                .enabled(true)
                .knowledgeBaseIds(List.of("not-a-uuid"))
                .retrievalStrategy("VECTOR")
                .build();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.augment(UUID.randomUUID(), "hello", request));

        assertEquals("Invalid knowledge base ID: not-a-uuid", ex.getMessage());
        verifyNoInteractions(ragSearchService);
    }

    private RagSearchResponse response(UUID knowledgeBaseId, List<RagSearchResult> results) {
        return RagSearchResponse.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .query("How does it work?")
                .embeddingProvider("OLLAMA")
                .embeddingModel("nomic-embed-text")
                .queryEmbeddingDimension(768)
                .topK(5)
                .results(results)
                .build();
    }

    private RagSearchResult result(String fileName, int chunkIndex, double similarity) {
        return RagSearchResult.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .fileName(fileName)
                .chunkIndex(chunkIndex)
                .content(fileName + " content")
                .metadataJson("{}")
                .similarity(similarity)
                .build();
    }
}
