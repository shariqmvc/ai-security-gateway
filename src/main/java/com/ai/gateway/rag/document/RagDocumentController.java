package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.rag.embedding.RagDocumentEmbeddingProcessor;
import com.ai.gateway.tenant.TenantAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class RagDocumentController {

    private final RagDocumentService service;
    private final RagDocumentUploadService uploadService;
    private final TenantAccessGuard tenantAccessGuard;
    private final RagDocumentEmbeddingProcessor embeddingProcessor;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse register(
            @PathVariable UUID knowledgeBaseId,
            @Valid @RequestBody DocumentRegistrationRequest request) {
        return service.register(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId,
                request);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(
            @PathVariable UUID knowledgeBaseId,
            @RequestPart("file") MultipartFile file) {
        return uploadService.upload(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId,
                file);
    }

    @PostMapping("/{documentId}/embed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse embed(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable UUID documentId) {
        UUID tenantId = tenantAccessGuard.requireAuthenticatedTenant();
        DocumentResponse response = service.get(tenantId, knowledgeBaseId, documentId);
        embeddingProcessor.processAsync(tenantId, documentId);
        return response;
    }

    @GetMapping
    public List<DocumentResponse> list(
            @PathVariable UUID knowledgeBaseId) {
        return service.list(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable UUID documentId) {
        return service.get(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId,
                documentId);
    }
}
