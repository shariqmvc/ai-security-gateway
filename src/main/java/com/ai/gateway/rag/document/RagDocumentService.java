package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;

import java.util.List;
import java.util.UUID;

public interface RagDocumentService {
    DocumentResponse register(UUID tenantId, UUID knowledgeBaseId,
                              DocumentRegistrationRequest request);
    List<DocumentResponse> list(UUID tenantId, UUID knowledgeBaseId);
    DocumentResponse get(UUID tenantId, UUID knowledgeBaseId, UUID documentId);
}
