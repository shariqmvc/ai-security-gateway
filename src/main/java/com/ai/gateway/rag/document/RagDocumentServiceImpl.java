package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagDocumentServiceImpl implements RagDocumentService {

    private final RagDocumentRepository repository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Override
    @Transactional
    public DocumentResponse register(UUID tenantId, UUID knowledgeBaseId,
                                     DocumentRegistrationRequest request) {
        requireTenant(tenantId);
        if (request == null) {
            throw new IllegalArgumentException("Document registration request is required.");
        }

        tenantSchemaRoutingService.useTenantSchema(tenantId);
        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Knowledge base not found: " + knowledgeBaseId));

        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot register a document in an archived knowledge base.");
        }

        LocalDateTime now = LocalDateTime.now();
        RagDocument document = RagDocument.builder()
                .id(UUID.randomUUID())
                .knowledgeBase(knowledgeBase)
                .fileName(request.getFileName().trim())
                .contentType(request.getContentType())
                .fileSizeBytes(request.getFileSizeBytes())
                .checksumSha256(request.getChecksumSha256())
                .status(DocumentStatus.REGISTERED)
                .content(request.getContent())
                .chunkCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(repository.save(document));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID tenantId, UUID knowledgeBaseId) {
        requireTenant(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        if (!knowledgeBaseRepository.existsById(knowledgeBaseId)) {
            throw new EntityNotFoundException(
                    "Knowledge base not found: " + knowledgeBaseId);
        }
        return repository.findByKnowledgeBase_IdOrderByCreatedAtDesc(knowledgeBaseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse get(UUID tenantId, UUID documentId) {
        requireTenant(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        return toResponse(repository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document not found: " + documentId)));
    }

    private void requireTenant(UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
    }

    private DocumentResponse toResponse(RagDocument entity) {
        return DocumentResponse.builder()
                .id(entity.getId())
                .knowledgeBaseId(entity.getKnowledgeBase().getId())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .fileSizeBytes(entity.getFileSizeBytes())
                .checksumSha256(entity.getChecksumSha256())
                .status(entity.getStatus())
                .chunkCount(entity.getChunkCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
