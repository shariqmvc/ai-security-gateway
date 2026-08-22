package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;

import java.util.UUID;

public interface RagSearchService {
    RagSearchResponse search(UUID tenantId, UUID knowledgeBaseId, RagSearchRequest request);
}
