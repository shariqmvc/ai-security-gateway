package com.ai.gateway.rag.document;

import com.ai.gateway.rag.document.dto.DocumentRegistrationRequest;
import com.ai.gateway.rag.document.dto.DocumentResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
@RequiredArgsConstructor
public class RagDocumentController {

    private final RagDocumentService service;
    private final TenantAccessGuard tenantAccessGuard;

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

    @GetMapping
    public List<DocumentResponse> list(
            @PathVariable UUID knowledgeBaseId) {
        return service.list(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId);
    }
}
