package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.rag.PersonalRagService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PersonalRagService personalRagService;

    @PostMapping
    public RagSearchResponse search(
            @PathVariable UUID knowledgeBaseId,
            @Valid @RequestBody RagSearchRequest request, HttpServletRequest httpRequest) {
        AuthenticationContext context=(AuthenticationContext)httpRequest.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if(context != null && context.isPersonalPrincipal()) return personalRagService.search(context,knowledgeBaseId,request);
        return searchService.search(tenantAccessGuard.requireAuthenticatedTenant(),knowledgeBaseId,request);
    }
}
