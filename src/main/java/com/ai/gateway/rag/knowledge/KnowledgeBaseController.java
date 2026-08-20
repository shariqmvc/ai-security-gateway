package com.ai.gateway.rag.knowledge;

import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseCreateRequest;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService service;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBaseResponse create(
            @Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return service.create(tenantAccessGuard.requireAuthenticatedTenant(), request);
    }

    @GetMapping
    public List<KnowledgeBaseResponse> list() {
        return service.list(tenantAccessGuard.requireAuthenticatedTenant());
    }

    @GetMapping("/{id}")
    public KnowledgeBaseResponse get(@PathVariable UUID id) {
        return service.get(tenantAccessGuard.requireAuthenticatedTenant(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        service.archive(tenantAccessGuard.requireAuthenticatedTenant(), id);
    }
}
