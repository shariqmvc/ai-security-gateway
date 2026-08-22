package com.ai.gateway.rag.embedding;

import com.ai.gateway.rag.document.RagDocument;
import com.ai.gateway.rag.document.RagDocumentChunk;
import com.ai.gateway.rag.document.RagDocumentChunkRepository;
import com.ai.gateway.rag.document.RagDocumentRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagEmbeddingPreparationService {
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagDocumentRepository documentRepository;
    private final RagDocumentChunkRepository chunkRepository;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Transactional(readOnly = true)
    public EmbeddingWork load(UUID tenantId, UUID documentId) {
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        RagDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("RAG document not found: " + documentId));
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(document.getKnowledgeBase().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Knowledge base not found: " + document.getKnowledgeBase().getId()));
        List<RagDocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            throw new IllegalStateException("Cannot embed a document with no chunks: " + documentId);
        }
        return new EmbeddingWork(
                knowledgeBase.getVectorStore(),
                knowledgeBase.getEmbeddingProvider(),
                knowledgeBase.getEmbeddingModel(),
                chunks.stream().map(RagDocumentChunk::getContent).toList());
    }

    public record EmbeddingWork(
            String vectorStore,
            String provider,
            String model,
            List<String> texts) {}
}
