package com.ai.gateway.rag.search;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderFactory;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.RagEmbeddingProperties;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseNotFoundException;
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
    private final RagEmbeddingProperties embeddingProperties = new RagEmbeddingProperties();
    private final TenantAccessGuard tenantAccessGuard = mock(TenantAccessGuard.class);
    private final TenantSchemaRoutingService tenantSchemaRoutingService = mock(TenantSchemaRoutingService.class);

    private final RagSearchServiceImpl service = new RagSearchServiceImpl(
            knowledgeBaseRepository,
            vectorSearchRepository,
            providerFactory,
            embeddingProperties,
            tenantAccessGuard,
            tenantSchemaRoutingService);

    @Test
    void shouldEmbedQueryAndReturnVectorSearchResults() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");

        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        EmbeddingVector queryVector = vector(1.0f, 2.0f, 3.0f);
        RagSearchResult result = result("phase3.md", 0,
                "AegisAI uses pgvector for semantic retrieval.", 0.91d);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("pgvector semantic search"), "nomic-embed-text"))
                .thenReturn(List.of(queryVector));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(5), eq(-1.0d)))
                .thenReturn(List.of(result));

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder()
                        .query("pgvector semantic search")
                        .topK(5)
                        .build());

        assertEquals(knowledgeBaseId, response.getKnowledgeBaseId());
        assertEquals("pgvector semantic search", response.getQuery());
        assertEquals("OLLAMA", response.getEmbeddingProvider());
        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        assertEquals(3, response.getQueryEmbeddingDimension());
        assertEquals(5, response.getTopK());
        assertEquals(1, response.getResults().size());
        assertEquals(0.91d, response.getResults().getFirst().getSimilarity());

        verify(tenantAccessGuard).requireAccess(tenantId);
        verify(tenantSchemaRoutingService).useTenantSchema(tenantId);
        verify(provider).embed(List.of("pgvector semantic search"), "nomic-embed-text");
        verify(vectorSearchRepository).search(
                knowledgeBaseId, "[1,2,3]", "OLLAMA", "nomic-embed-text", 3, 5, -1.0d);
    }

    @Test
    void shouldPassMinScoreAndTopKToVectorRepository() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("test query"), "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(10), eq(0.75d)))
                .thenReturn(List.of());

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder()
                        .query("test query")
                        .topK(10)
                        .minScore(0.75d)
                        .build());

        assertTrue(response.getResults().isEmpty());
        verify(vectorSearchRepository).search(
                knowledgeBaseId, "[1,2,3]", "OLLAMA", "nomic-embed-text", 3, 10, 0.75d);
    }

    @Test
    void shouldRejectNullRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(tenantId, knowledgeBaseId, null));

        assertEquals("RAG search request is required.", ex.getMessage());
        verify(tenantAccessGuard).requireAccess(tenantId);
        verifyNoInteractions(knowledgeBaseRepository, providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectBlankQuery() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("   ").build()));

        assertEquals("Search query is required.", ex.getMessage());
        verify(tenantAccessGuard).requireAccess(tenantId);
        verifyNoInteractions(knowledgeBaseRepository, providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectInvalidTopKBeforeEmbedding() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").topK(101).build()));

        assertEquals("topK must be between 1 and 100.", ex.getMessage());
        verifyNoInteractions(knowledgeBaseRepository, providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectInvalidMinScoreBeforeEmbedding() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").minScore(1.01d).build()));

        assertEquals("minScore must be between -1.0 and 1.0.", ex.getMessage());
        verifyNoInteractions(knowledgeBaseRepository, providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectNonFiniteMinScoreBeforeEmbedding() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").minScore(Double.NaN).build()));

        assertEquals("minScore must be a finite number.", ex.getMessage());
        verifyNoInteractions(knowledgeBaseRepository, providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectMissingKnowledgeBase() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.empty());

        KnowledgeBaseNotFoundException ex = assertThrows(
                KnowledgeBaseNotFoundException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").build()));

        assertEquals("Knowledge base not found: " + knowledgeBaseId, ex.getMessage());
        verify(tenantAccessGuard).requireAccess(tenantId);
        verify(tenantSchemaRoutingService).useTenantSchema(tenantId);
        verifyNoInteractions(providerFactory, vectorSearchRepository);
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

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").build()));

        assertTrue(ex.getMessage().contains("archived"));
        verifyNoInteractions(providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldRejectNonPgvectorKnowledgeBase() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");
        knowledgeBase.setVectorStore("OTHER");

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test").build()));

        assertTrue(ex.getMessage().contains("RAG vector search requires vectorStore=PGVECTOR"));
        verifyNoInteractions(providerFactory, vectorSearchRepository);
    }

    @Test
    void shouldUseProviderDefaultModelWhenKnowledgeBaseModelMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", null);
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.defaultModel()).thenReturn("nomic-embed-text");
        when(provider.embed(List.of("test query"), "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(5), eq(-1.0d)))
                .thenReturn(List.of());

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder().query("test query").build());

        assertEquals("nomic-embed-text", response.getEmbeddingModel());
        verify(provider).defaultModel();
        verify(provider).embed(List.of("test query"), "nomic-embed-text");
    }

    @Test
    void shouldRejectMultipleQueryEmbeddings() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("test query"), "nomic-embed-text"))
                .thenReturn(List.of(
                        vector(1.0f, 2.0f, 3.0f),
                        vector(4.0f, 5.0f, 6.0f)));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> service.search(
                        tenantId,
                        knowledgeBaseId,
                        RagSearchRequest.builder().query("test query").build()));

        assertEquals(
                "Embedding provider returned 2 vectors for one search query.",
                ex.getMessage());
        verifyNoInteractions(vectorSearchRepository);
    }

    @Test
    void shouldTrimQueryBeforeEmbeddingAndResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("test query"), "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(5), eq(-1.0d)))
                .thenReturn(List.of());

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder().query("  test query  ").build());

        assertEquals("test query", response.getQuery());
        verify(provider).embed(List.of("test query"), "nomic-embed-text");
    }

    @Test
    void shouldReturnMultipleRankedResults() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, "OLLAMA", "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        RagSearchResult first = result(
                "tenant-b-security.md", 0,
                "Tenant B requires strict retrieval isolation and API-key authentication.",
                0.8055d);
        RagSearchResult second = result(
                "tenant-b-isolation.md", 0,
                "This document belongs exclusively to TENANT-B.",
                0.7845d);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(
                List.of("How does Tenant B protect retrieval access?"),
                "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(3), eq(-1.0d)))
                .thenReturn(List.of(first, second));

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder()
                        .query("How does Tenant B protect retrieval access?")
                        .topK(3)
                        .build());

        assertEquals(2, response.getResults().size());
        assertEquals("tenant-b-security.md", response.getResults().get(0).getFileName());
        assertEquals("tenant-b-isolation.md", response.getResults().get(1).getFileName());
        assertTrue(response.getResults().get(0).getSimilarity()
                > response.getResults().get(1).getSimilarity());
    }

    @Test
    void shouldNormalizeConfiguredProviderToUpperCase() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, " ollama ", "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("test"), "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(5), eq(-1.0d)))
                .thenReturn(List.of());

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder().query("test").build());

        assertEquals("OLLAMA", response.getEmbeddingProvider());
        verify(providerFactory).get("OLLAMA");
    }

    @Test
    void shouldUseDefaultProviderWhenKnowledgeBaseProviderMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        KnowledgeBase knowledgeBase = activePgVectorKnowledgeBase(
                knowledgeBaseId, null, "nomic-embed-text");
        EmbeddingProvider provider = mock(EmbeddingProvider.class);

        when(knowledgeBaseRepository.findById(knowledgeBaseId)).thenReturn(Optional.of(knowledgeBase));
        when(providerFactory.get("OLLAMA")).thenReturn(provider);
        when(provider.embed(List.of("test"), "nomic-embed-text"))
                .thenReturn(List.of(vector(1.0f, 2.0f, 3.0f)));
        when(vectorSearchRepository.search(
                eq(knowledgeBaseId), eq("[1,2,3]"), eq("OLLAMA"), eq("nomic-embed-text"),
                eq(3), eq(5), eq(-1.0d)))
                .thenReturn(List.of());

        RagSearchResponse response = service.search(
                tenantId,
                knowledgeBaseId,
                RagSearchRequest.builder().query("test").build());

        assertEquals("OLLAMA", response.getEmbeddingProvider());
        verify(providerFactory).get("OLLAMA");
    }

    private KnowledgeBase activePgVectorKnowledgeBase(
            UUID id,
            String provider,
            String model) {
        return KnowledgeBase.builder()
                .id(id)
                .name("Test KB")
                .status(KnowledgeBaseStatus.ACTIVE)
                .embeddingProvider(provider)
                .embeddingModel(model)
                .vectorStore("PGVECTOR")
                .build();
    }

    private EmbeddingVector vector(float... values) {
        List<Float> floats = new java.util.ArrayList<>(values.length);
        for (float value : values) {
            floats.add(value);
        }
        return new EmbeddingVector(floats);
    }

    private RagSearchResult result(
            String fileName,
            int chunkIndex,
            String content,
            double similarity) {
        return RagSearchResult.builder()
                .id(UUID.randomUUID())
                .documentId(UUID.randomUUID())
                .fileName(fileName)
                .chunkIndex(chunkIndex)
                .content(content)
                .metadataJson("{\"chunkIndex\":" + chunkIndex + "}")
                .similarity(similarity)
                .build();
    }
}
