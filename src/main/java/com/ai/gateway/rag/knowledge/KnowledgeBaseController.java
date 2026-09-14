package com.ai.gateway.rag.knowledge;

import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseCreateRequest;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseResponse;
import com.ai.gateway.authentication.AuthenticationConstants;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.rag.PersonalRagService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PersonalRagService personalRagService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBaseResponse create(
            @Valid @RequestBody KnowledgeBaseCreateRequest request, HttpServletRequest httpRequest) {
        AuthenticationContext context = context(httpRequest);
        if (context != null && context.isPersonalPrincipal()) return personalRagService.create(context, request);
        return service.create(tenantAccessGuard.requireAuthenticatedTenant(), request);
    }

    @GetMapping
    public List<KnowledgeBaseResponse> list(HttpServletRequest httpRequest) {
        AuthenticationContext context = context(httpRequest);
        if (context != null && context.isPersonalPrincipal()) return personalRagService.list(context);
        return service.list(tenantAccessGuard.requireAuthenticatedTenant());
    }

    @GetMapping("/{id}")
    public KnowledgeBaseResponse get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        AuthenticationContext context = context(httpRequest);
        if (context != null && context.isPersonalPrincipal()) return personalRagService.get(context, id);
        return service.get(tenantAccessGuard.requireAuthenticatedTenant(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id, HttpServletRequest httpRequest) {
        AuthenticationContext context = context(httpRequest);
        if (context != null && context.isPersonalPrincipal()) { personalRagService.archive(context, id); return; }
        service.archive(tenantAccessGuard.requireAuthenticatedTenant(), id);
    }

    private AuthenticationContext context(HttpServletRequest request) {
        AuthenticationContext context = (AuthenticationContext) request.getAttribute(AuthenticationConstants.AUTH_CONTEXT);
        if (context == null) throw new org.springframework.security.access.AccessDeniedException("Authenticated principal is required.");
        return context;
    }
}
