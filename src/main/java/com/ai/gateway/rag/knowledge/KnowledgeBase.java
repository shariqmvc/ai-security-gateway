package com.ai.gateway.rag.knowledge;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "KNOWLEDGE_BASE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBase {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private KnowledgeBaseStatus status;

    @Column(name = "embedding_provider", length = 64)
    private String embeddingProvider;

    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;

    @Column(name = "vector_store", nullable = false, length = 64)
    private String vectorStore;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunking_strategy", nullable = false, length = 64)
    private ChunkingStrategy chunkingStrategy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
