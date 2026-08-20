package com.ai.gateway.rag.knowledge;

import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseCreateRequest;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseResponse;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseService {
    KnowledgeBaseResponse create(UUID tenantId, KnowledgeBaseCreateRequest request);
    List<KnowledgeBaseResponse> list(UUID tenantId);
    KnowledgeBaseResponse get(UUID tenantId, UUID knowledgeBaseId);
    void archive(UUID tenantId, UUID knowledgeBaseId);
}
