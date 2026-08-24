package com.ai.gateway.rag.search.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class RagSearchResponse {
    UUID knowledgeBaseId;
    String query;
    String retrievalStrategy;
    String embeddingProvider;
    String embeddingModel;
    int queryEmbeddingDimension;
    int topK;
    List<RagSearchResult> results;
}
