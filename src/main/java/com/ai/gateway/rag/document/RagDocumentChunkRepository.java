package com.ai.gateway.rag.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RagDocumentChunkRepository extends JpaRepository<RagDocumentChunk, UUID> {
    List<RagDocumentChunk> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);
}
