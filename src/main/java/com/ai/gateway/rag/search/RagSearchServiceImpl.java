package com.ai.gateway.rag.search;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.rag.embedding.EmbeddingProvider;
import com.ai.gateway.rag.embedding.EmbeddingProviderFactory;
import com.ai.gateway.rag.embedding.EmbeddingVector;
import com.ai.gateway.rag.embedding.EmbeddingVectorFormatter;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.rag.knowledge.KnowledgeBaseNotFoundException;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagVectorSearchRepository vectorSearchRepository;
    private final EmbeddingProviderFactory providerFactory;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Override
    @Transactional(readOnly = true)
    public RagSearchResponse search(
            UUID tenantId,
            UUID knowledgeBaseId,
            RagSearchRequest request) {

        tenantAccessGuard.requireAccess(tenantId);
        validateRequest(request);

        tenantSchemaRoutingService.useTenantSchema(tenantId);

        KnowledgeBase knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new KnowledgeBaseNotFoundException(knowledgeBaseId));

        if (knowledgeBase.getStatus() != KnowledgeBaseStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot search an archived knowledge base: " + knowledgeBaseId);
        }

        if (!"PGVECTOR".equalsIgnoreCase(trimToNull(knowledgeBase.getVectorStore()))) {
            throw new BusinessException(
                    "RAG vector search requires vectorStore=PGVECTOR; configured value: "
                            + knowledgeBase.getVectorStore());
        }

        String providerName = normalizeProvider(knowledgeBase.getEmbeddingProvider());
        EmbeddingProvider provider = providerFactory.get(providerName);

        String model = trimToNull(knowledgeBase.getEmbeddingModel());
        if (model == null) {
            model = provider.defaultModel();
        }

        List<EmbeddingVector> embeddings = provider.embed(
                List.of(request.getQuery().trim()), model);

        if (embeddings.size() != 1) {
            throw new BusinessException(
                    "Embedding provider returned " + embeddings.size()
                            + " vectors for one search query.");
        }

        EmbeddingVector queryEmbedding = embeddings.getFirst();
        int dimension = queryEmbedding.dimension();

        List<RagSearchResult> results = vectorSearchRepository.search(
                knowledgeBaseId,
                EmbeddingVectorFormatter.toPgVector(queryEmbedding),
                providerName,
                model,
                dimension,
                request.getTopK(),
                request.getMinScore());

        return RagSearchResponse.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .query(request.getQuery().trim())
                .embeddingProvider(providerName)
                .embeddingModel(model)
                .queryEmbeddingDimension(dimension)
                .topK(request.getTopK())
                .results(results)
                .build();
    }

    private void validateRequest(RagSearchRequest request) {
        if (request == null) {
            throw new BusinessException("RAG search request is required.");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new BusinessException("Search query is required.");
        }
        if (request.getTopK() < 1 || request.getTopK() > 100) {
            throw new BusinessException("topK must be between 1 and 100.");
        }
        if (request.getMinScore() < -1.0d || request.getMinScore() > 1.0d) {
            throw new BusinessException("minScore must be between -1.0 and 1.0.");
        }
    }

    private String normalizeProvider(String provider) {
        String normalized = trimToNull(provider);
        return normalized == null
                ? "OLLAMA"
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
