package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/search")
@RequiredArgsConstructor
public class RagSearchController {

    private final RagSearchService searchService;
    private final TenantAccessGuard tenantAccessGuard;

    @PostMapping
    public RagSearchResponse search(
            @PathVariable UUID knowledgeBaseId,
            @Valid @RequestBody RagSearchRequest request) {
        return searchService.search(
                tenantAccessGuard.requireAuthenticatedTenant(),
                knowledgeBaseId,
                request);
    }
}
