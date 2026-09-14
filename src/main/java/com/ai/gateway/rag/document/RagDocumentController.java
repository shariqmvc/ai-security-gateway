package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.rag.PersonalRagService;
import jakarta.servlet.http.HttpServletRequest;
import com.ai.gateway.rag.embedding.RagDocumentEmbeddingProcessor;
import com.ai.gateway.tenant.TenantAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final PersonalRagService personalRagService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse register(
            @PathVariable UUID knowledgeBaseId,
            @Valid @RequestBody DocumentRegistrationRequest request, HttpServletRequest httpRequest) {
        AuthenticationContext context=context(httpRequest);
        if(context != null && context.isPersonalPrincipal()) return personalRagService.register(context,knowledgeBaseId,request);
        return service.register(tenantAccessGuard.requireAuthenticatedTenant(),knowledgeBaseId,request);
    }

    /**
     * Multipart upload at the canonical /documents endpoint.
     * The JSON registration endpoint above is distinguished by its consumes
     * constraint, so clients may use either JSON registration or multipart
     * upload without relying on an implementation-specific /upload suffix.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse uploadMultipart(
            @PathVariable UUID knowledgeBaseId,
            @RequestPart("file") MultipartFile file, HttpServletRequest httpRequest) {
        AuthenticationContext context = context(httpRequest);
        if (context.isPersonalPrincipal()) {
            return personalRagService.upload(context, knowledgeBaseId, file);
        }
        return uploadService.upload(tenantAccessGuard.requireAuthenticatedTenant(), knowledgeBaseId, file);
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse upload(
            @PathVariable UUID knowledgeBaseId,
            @RequestPart("file") MultipartFile file, HttpServletRequest httpRequest) {
        AuthenticationContext context=context(httpRequest);
        if(context != null && context.isPersonalPrincipal()) return personalRagService.upload(context,knowledgeBaseId,file);
        return uploadService.upload(tenantAccessGuard.requireAuthenticatedTenant(),knowledgeBaseId,file);
    }

    @PostMapping("/{documentId}/embed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentResponse embed(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable UUID documentId, HttpServletRequest httpRequest) {
        AuthenticationContext context=context(httpRequest);
        if(context != null && context.isPersonalPrincipal()) {
            DocumentResponse response=personalRagService.getDocument(context,knowledgeBaseId,documentId);
            personalRagService.embedAsync(context,knowledgeBaseId,documentId);
            return response;
        }
        UUID tenantId = tenantAccessGuard.requireAuthenticatedTenant();
        DocumentResponse response = service.get(tenantId, knowledgeBaseId, documentId);
        embeddingProcessor.processAsync(tenantId, documentId);
        return response;
    }

    @GetMapping
    public List<DocumentResponse> list(
            @PathVariable UUID knowledgeBaseId, HttpServletRequest httpRequest) {
        AuthenticationContext context=context(httpRequest);
        if(context != null && context.isPersonalPrincipal()) return personalRagService.listDocuments(context,knowledgeBaseId);
        return service.list(tenantAccessGuard.requireAuthenticatedTenant(),knowledgeBaseId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(
            @PathVariable UUID knowledgeBaseId,
            @PathVariable UUID documentId, HttpServletRequest httpRequest) {
        AuthenticationContext context=context(httpRequest);
        if(context != null && context.isPersonalPrincipal()) return personalRagService.getDocument(context,knowledgeBaseId,documentId);
        return service.get(tenantAccessGuard.requireAuthenticatedTenant(),knowledgeBaseId,documentId);
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext context=(AuthenticationContext)request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if(context==null) throw new org.springframework.security.access.AccessDeniedException("Authenticated principal is required.");
        return context;
    }
}
