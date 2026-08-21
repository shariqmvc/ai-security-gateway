package com.ai.gateway.rag.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
    List<RagDocument> findByKnowledgeBase_IdOrderByCreatedAtDesc(UUID knowledgeBaseId);
    long countByKnowledgeBase_Id(UUID knowledgeBaseId);
    boolean existsByKnowledgeBase_IdAndChecksumSha256(UUID knowledgeBaseId, String checksumSha256);
}
