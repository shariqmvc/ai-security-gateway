package com.ai.gateway.rag.document;

import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.EmbeddingVectorFormatter;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagEmbeddingPersistenceService {
    private final JdbcTemplate jdbcTemplate;
    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Transactional
    public void markEmbedding(UUID tenantId, UUID documentId) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = find(documentId);
        document.setStatus(DocumentStatus.EMBEDDING);
        document.setErrorMessage(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    public void persistEmbeddings(UUID tenantId, UUID documentId, String provider, String model,
                                  List<EmbeddingVector> embeddings) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        List<RagDocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.size() != embeddings.size()) {
            throw new IllegalStateException("Embedding count " + embeddings.size()
                    + " does not match chunk count " + chunks.size());
        }
        int dimension = embeddings.getFirst().dimension();
        if (embeddings.stream().anyMatch(vector -> vector.dimension() != dimension)) {
            throw new IllegalStateException("Embedding provider returned vectors with inconsistent dimensions.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < chunks.size(); i++) {
            RagDocumentChunk chunk = chunks.get(i);
            EmbeddingVector vector = embeddings.get(i);
            jdbcTemplate.update("""
                    UPDATE RAG_DOCUMENT_CHUNK
                       SET embedding = ?::public.vector,
                           embedding_provider = ?,
                           embedding_model = ?,
                           embedding_dimension = ?,
                           embedded_at = ?
                     WHERE id = ?
                    """,
                    EmbeddingVectorFormatter.toPgVector(vector),
                    provider,
                    model,
                    vector.dimension(),
                    now,
                    chunk.getId());
        }
        RagDocument document = find(documentId);
        document.setStatus(DocumentStatus.INDEXED);
        document.setErrorMessage(null);
        document.setUpdatedAt(now);
        documentRepository.save(document);
    }

    @Transactional
    public void markFailed(UUID tenantId, UUID documentId, String errorMessage) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = find(documentId);
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(errorMessage == null ? "Embedding failed." : truncate(errorMessage, 2000));
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    private RagDocument find(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("RAG document not found during embedding: " + id));
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
