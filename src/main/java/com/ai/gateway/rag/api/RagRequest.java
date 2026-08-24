package com.ai.gateway.rag.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagRequest {

    @Builder.Default
    private boolean enabled = false;

    @Size(max = 10, message = "A maximum of 10 knowledge bases may be used per request.")
    @Builder.Default
    private List<String> knowledgeBaseIds = Collections.emptyList();

    @Min(1)
    @Max(100)
    @Builder.Default
    private int topK = 5;

    /**
     * Retrieval strategy for Phase 4.2:
     * VECTOR, KEYWORD, HYBRID, or HYBRID_RERANKED.
     */
    @Builder.Default
    private String retrievalStrategy = "VECTOR";

    /** Minimum cosine similarity required for retrieved context. */
    @DecimalMin(value = "-1.0", message = "RAG minScore must be at least -1.0.")
    @DecimalMax(value = "1.0", message = "RAG minScore must not exceed 1.0.")
    @Builder.Default
    private double minScore = 0.70d;

    @Builder.Default
    private boolean queryTransformation = false;

    @Min(1)
    @Max(200)
    @Builder.Default
    private int candidateLimit = 50;

    @Min(256)
    @Max(32768)
    @Builder.Default
    private int contextTokenBudget = 4000;
}
