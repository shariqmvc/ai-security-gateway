package com.ai.gateway.rag.knowledge.dto;

import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
public class KnowledgeBaseResponse {
    private UUID id;
    private String name;
    private String description;
    private KnowledgeBaseStatus status;
    private String embeddingProvider;
    private String embeddingModel;
    private String vectorStore;
    private ChunkingStrategy chunkingStrategy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
