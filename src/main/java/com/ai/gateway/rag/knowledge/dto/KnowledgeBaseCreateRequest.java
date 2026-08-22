package com.ai.gateway.rag.knowledge.dto;

import com.ai.gateway.rag.knowledge.ChunkingStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeBaseCreateRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 64)
    private String embeddingProvider;

    @Size(max = 255)
    private String embeddingModel;

    @Builder.Default
    private String vectorStore = "PGVECTOR";

    @Builder.Default
    private ChunkingStrategy chunkingStrategy = ChunkingStrategy.TOKEN_AWARE;
}
