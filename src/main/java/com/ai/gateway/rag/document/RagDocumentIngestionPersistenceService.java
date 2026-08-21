package com.ai.gateway.rag.document;

import com.ai.gateway.rag.ingestion.DocumentChunk;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transactional persistence boundary for the asynchronous ingestion worker.
 * The worker parses outside a DB transaction and only opens short tenant-scoped
 * transactions for state transitions and chunk persistence.
 */
@Service
@RequiredArgsConstructor
public class RagDocumentIngestionPersistenceService {

    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Transactional
    public void markProcessing(UUID tenantId, UUID documentId) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = find(documentId);
        document.setStatus(DocumentStatus.PROCESSING);
        document.setErrorMessage(null);
        document.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional
    public void complete(UUID tenantId,
                         UUID documentId,
                         String content,
                         String detectedContentType,
                         List<DocumentChunk> chunks) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = find(documentId);

        chunkRepository.deleteByDocumentId(documentId);

        List<RagDocumentChunk> entities = chunks.stream()
                .map(chunk -> RagDocumentChunk.builder()
                        .id(UUID.randomUUID())
                        .documentId(documentId)
                        .chunkIndex(chunk.index())
                        .content(chunk.content())
                        .tokenCount(chunk.tokenCount())
                        .metadataJson(chunk.metadataJson())
                        .createdAt(LocalDateTime.now())
                        .build())
                .toList();

        chunkRepository.saveAll(entities);

        document.setContent(content);
        if (document.getContentType() == null || document.getContentType().isBlank()) {
            document.setContentType(detectedContentType);
        }
        document.setChunkCount(entities.size());
        document.setStatus(DocumentStatus.READY_FOR_EMBEDDING);
        document.setErrorMessage(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    @Transactional
    public void markFailed(UUID tenantId, UUID documentId, String errorMessage) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = find(documentId);
        document.setStatus(DocumentStatus.FAILED);
        document.setErrorMessage(truncate(errorMessage, 2000));
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    private RagDocument find(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException(
                        "RAG document not found during ingestion: " + documentId));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "Document ingestion failed.";
        }
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}
