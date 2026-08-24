package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;

import java.util.List;

public interface RagReranker {
    List<RagSearchResult> rerank(String query, List<RagSearchResult> candidates, int limit);
}
