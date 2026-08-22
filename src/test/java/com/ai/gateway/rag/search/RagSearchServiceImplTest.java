package com.ai.gateway.rag.search;

import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderFactory;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagSearchServiceImplTest {

    private final KnowledgeBaseRepository knowledgeBaseRepository = mock(KnowledgeBaseRepository.class);
    private final RagVectorSearchRepository vectorSearchRepository = mock(RagVectorSearchRepository.class);
    private final EmbeddingProviderFactory providerFactory = mock(EmbeddingProviderFactory.class);
    private final TenantAccessGuard tenantAccessGuard = mock(TenantAccessGuard.class);
    private final TenantSchemaRoutingService tenantSchemaRoutingService = mock(TenantSchemaRoutingService.class);

    private final RagSearchServiceImpl service = new RagSearchServiceImpl(
            knowledgeBaseRepository,
            vectorSearchRepository,
            providerFactory,
            tenantAccessGuard,
            tenantSchemaRoutingService);

    @Test
    void shouldEmbedQueryAndReturnVectorSearchResults() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(knowledgeBaseId)
                .name("Test KB")
                .status(KnowledgeBaseStatus.ACTIVE)
                .embeddingProvider("OLLAMA")
                .embeddingModel("nomic-embed-text")
                .vectorStore("PGVECTOR")
                .build();

        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        EmbeddingVector queryVector = new EmbeddingVector(List.of(1.0f, 2.0f, 3.0f));
        RagSearchResult result = RagSearchResult.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .fileName("phase3.md")
                .chunkIndex(0)
                .content("AegisAI uses pgvector for semantic retrieval.")
                .similarity(0.91d)
                .build();

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.defaultModel()).thenReturn("nomic-embed-text");
        when(provider.embed(List.of("pgvector semantic search"), "nomic-embed-text"))
                .thenReturn(List.of(queryVector));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId),
                eq("[1,2,3]"),
                eq("OLLAMA"),
                eq("nomic-embed-text"),
                eq(3),
                eq(5),
                eq(-1.0d)))
                .thenReturn(List.of(result));

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder()
                        .query("pgvector semantic search")
                        .topK(5)
                        .build());

        assertEquals(knowledgeBaseId, response.getKnowledgeBaseId());
        assertEquals("OLLAMA", response.getEmbeddingProvider());
        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        assertEquals(3, response.getQueryEmbeddingDimension());
        assertEquals(1, response.getResults().size());
        assertEquals(0.91d, response.getResults().getFirst().getSimilarity());

        verify(tenantAccessGuard).requireAccess(tenantId);
        verify(tenantSchemaRoutingService).useTenantSchema(tenantId);
    }

    @Test
    void shouldRejectArchivedKnowledgeBase() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(knowledgeBaseId)
                .name("Archived KB")
                .status(KnowledgeBaseStatus.ARCHIVED)
                .vectorStore("PGVECTOR")
                .build();

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").build()));

        assertTrue(ex.getMessage().contains("archived"));
        verifyNoInteractions(providerFactory, vectorSearchRepository);
    }
}
