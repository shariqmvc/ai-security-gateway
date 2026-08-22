package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;

import java.util.List;
import java.util.UUID;

public interface RagVectorSearchRepository {

    List<RagSearchResult> search(
            UUID knowledgeBaseId,
            String queryVector,
            String embeddingProvider,
            String embeddingModel,
            int embeddingDimension,
            int topK,
            double minScore);
}
