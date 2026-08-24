package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;

import java.util.List;
import java.util.UUID;

public interface RagKeywordSearchRepository {

    List<RagSearchResult> search(
            UUID knowledgeBaseId,
            String query,
            int limit);
}
