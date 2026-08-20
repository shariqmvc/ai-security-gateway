package com.ai.gateway.rag.knowledge;

import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseCreateRequest;
import com.ai.gateway.rag.knowledge.dto.KnowledgeBaseResponse;
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
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Override
    @Transactional
    public KnowledgeBaseResponse create(UUID tenantId, KnowledgeBaseCreateRequest request) {
        requireTenant(tenantId);
        if (request == null) {
            throw new IllegalArgumentException("Knowledge base request is required.");
        }
        // Establish tenant search_path before any repository access.
        // Otherwise the schema-neutral JPA repository would query the public
        // compatibility table instead of the authoritative tenant schema.
        tenantSchemaRoutingService.useTenantSchema(tenantId);

        if (repository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new IllegalArgumentException(
                    "Knowledge base already exists: " + request.getName());
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase entity = KnowledgeBase.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .description(request.getDescription())
                .status(KnowledgeBaseStatus.ACTIVE)
                .embeddingProvider(normalize(request.getEmbeddingProvider()))
                .embeddingModel(normalize(request.getEmbeddingModel()))
                .vectorStore(defaultIfBlank(request.getVectorStore(), "POSTGRES"))
                .chunkingStrategy(request.getChunkingStrategy() == null
                        ? ChunkingStrategy.TOKEN_AWARE
                        : request.getChunkingStrategy())
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KnowledgeBaseResponse> list(UUID tenantId) {
        requireTenant(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        return repository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeBaseResponse get(UUID tenantId, UUID knowledgeBaseId) {
        requireTenant(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        return toResponse(find(knowledgeBaseId));
    }

    @Override
    @Transactional
    public void archive(UUID tenantId, UUID knowledgeBaseId) {
        requireTenant(tenantId);
        tenantSchemaRoutingService.useTenantSchema(tenantId);
        KnowledgeBase entity = find(knowledgeBaseId);
        entity.setStatus(KnowledgeBaseStatus.ARCHIVED);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private KnowledgeBase find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Knowledge base not found: " + id));
    }

    private void requireTenant(UUID tenantId) {
        tenantAccessGuard.requireAccess(tenantId);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase entity) {
        return KnowledgeBaseResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .embeddingProvider(entity.getEmbeddingProvider())
                .embeddingModel(entity.getEmbeddingModel())
                .vectorStore(entity.getVectorStore())
                .chunkingStrategy(entity.getChunkingStrategy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
