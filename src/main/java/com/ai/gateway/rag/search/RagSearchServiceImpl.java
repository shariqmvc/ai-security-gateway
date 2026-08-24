package com.ai.gateway.rag.search;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.rag.embedding.*;
import com.ai.gateway.rag.knowledge.KnowledgeBase;
import com.ai.gateway.rag.knowledge.KnowledgeBaseRepository;
import com.ai.gateway.rag.knowledge.KnowledgeBaseStatus;
import com.ai.gateway.rag.knowledge.KnowledgeBaseNotFoundException;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import com.ai.gateway.tenant.TenantAccessGuard;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RagSearchServiceImpl implements RagSearchService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final RagVectorSearchRepository vectorSearchRepository;
    private final RagKeywordSearchRepository keywordSearchRepository;
    private final RagResultFusionService fusionService;
    private final RagReranker reranker;
    private final RagQueryTransformer queryTransformer;
    private final EmbeddingProviderFactory providerFactory;
    private final RagEmbeddingProperties embeddingProperties;
    private final TenantAccessGuard tenantAccessGuard;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Autowired
    public RagSearchServiceImpl(
            KnowledgeBaseRepository knowledgeBaseRepository,
            RagVectorSearchRepository vectorSearchRepository,
            RagKeywordSearchRepository keywordSearchRepository,
            RagResultFusionService fusionService,
            RagReranker reranker,
            RagQueryTransformer queryTransformer,
            EmbeddingProviderFactory providerFactory,
            RagEmbeddingProperties embeddingProperties,
            TenantAccessGuard tenantAccessGuard,
            TenantSchemaRoutingService tenantSchemaRoutingService) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.vectorSearchRepository = vectorSearchRepository;
        this.keywordSearchRepository = keywordSearchRepository;
        this.fusionService = fusionService;
        this.reranker = reranker;
        this.queryTransformer = queryTransformer;
        this.providerFactory = providerFactory;
        this.embeddingProperties = embeddingProperties;
        this.tenantAccessGuard = tenantAccessGuard;
        this.tenantSchemaRoutingService = tenantSchemaRoutingService;
    }

    /**
     * Backward-compatible constructor for existing unit tests and integrations
     * that exercise the original VECTOR-only retrieval path.
     */
    public RagSearchServiceImpl(
            KnowledgeBaseRepository knowledgeBaseRepository,
            RagVectorSearchRepository vectorSearchRepository,
            EmbeddingProviderFactory providerFactory,
            RagEmbeddingProperties embeddingProperties,
            TenantAccessGuard tenantAccessGuard,
            TenantSchemaRoutingService tenantSchemaRoutingService) {
        this(
                knowledgeBaseRepository,
                vectorSearchRepository,
                null,
                new RagResultFusionService(),
                new TokenOverlapRagReranker(),
                new DefaultRagQueryTransformer(),
                providerFactory,
                embeddingProperties,
                tenantAccessGuard,
                tenantSchemaRoutingService);
    }

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
                    "RAG retrieval requires vectorStore=PGVECTOR; configured value: "
                            + knowledgeBase.getVectorStore());
        }

        RagRetrievalStrategy strategy = RagRetrievalStrategy.from(request.getRetrievalStrategy());
        String query = request.getQuery().trim();
        List<String> queries = request.isQueryTransformation()
                ? queryTransformer.transform(query)
                : List.of(query);

        String providerName = null;
        String model = null;
        List<RagSearchResult> vectorResults = new ArrayList<>();
        int dimension = 0;

        if (strategy != RagRetrievalStrategy.KEYWORD) {
            providerName = normalizeProvider(knowledgeBase.getEmbeddingProvider());
            EmbeddingProvider provider = providerFactory.get(providerName);

            model = trimToNull(knowledgeBase.getEmbeddingModel());
            if (model == null) {
                model = provider.defaultModel();
            }
            for (String transformedQuery : queries) {
                EmbeddingVector embedding = singleEmbedding(provider, transformedQuery, model);
                dimension = embedding.dimension();

                vectorResults.addAll(vectorSearchRepository.search(
                        knowledgeBaseId,
                        EmbeddingVectorFormatter.toPgVector(embedding),
                        providerName,
                        model,
                        dimension,
                        request.getCandidateLimit(),
                        request.getMinScore()));
            }
            vectorResults = deduplicate(vectorResults, request.getCandidateLimit());
        }

        List<RagSearchResult> keywordResults = List.of();
        if (strategy == RagRetrievalStrategy.KEYWORD
                || strategy == RagRetrievalStrategy.HYBRID
                || strategy == RagRetrievalStrategy.HYBRID_RERANKED) {

            List<RagSearchResult> all = new ArrayList<>();
            for (String transformedQuery : queries) {
                all.addAll(keywordSearchRepository.search(
                        knowledgeBaseId,
                        transformedQuery,
                        request.getCandidateLimit()));
            }
            keywordResults = deduplicate(all, request.getCandidateLimit());
        }

        List<RagSearchResult> results;
        if (strategy == RagRetrievalStrategy.VECTOR) {
            results = vectorResults;
        } else if (strategy == RagRetrievalStrategy.KEYWORD) {
            results = keywordResults;
        } else {
            results = fusionService.fuse(
                    vectorResults, keywordResults, request.getCandidateLimit());
            if (strategy == RagRetrievalStrategy.HYBRID_RERANKED) {
                results = reranker.rerank(query, results, request.getCandidateLimit());
            }
        }

        // minScore is a final retrieval-quality gate. This is intentionally
        // applied after fusion/reranking so a low-scoring keyword candidate
        // cannot bypass the threshold in HYBRID retrieval.
        results = filterByMinScore(results, request.getMinScore())
                .stream()
                .limit(request.getTopK())
                .toList();

        return RagSearchResponse.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .query(query)
                .retrievalStrategy(strategy.name())
                .embeddingProvider(providerName)
                .embeddingModel(model)
                .queryEmbeddingDimension(dimension)
                .topK(request.getTopK())
                .results(results)
                .build();
    }

    private EmbeddingVector singleEmbedding(
            EmbeddingProvider provider, String query, String model) {

        List<EmbeddingVector> embeddings = provider.embed(List.of(query), model);
        if (embeddings.size() != 1) {
            throw new BusinessException(
                    "Embedding provider returned " + embeddings.size()
                            + " vectors for one search query.");
        }
        return embeddings.getFirst();
    }

    private List<RagSearchResult> filterByMinScore(
            List<RagSearchResult> results, double minScore) {

        if (minScore <= -1.0d) {
            return results;
        }

        return results.stream()
                .filter(result -> Double.isFinite(result.getSimilarity()))
                .filter(result -> result.getSimilarity() >= minScore)
                .toList();
    }

    private List<RagSearchResult> deduplicate(
            List<RagSearchResult> results, int limit) {

        Map<UUID, RagSearchResult> unique = new LinkedHashMap<>();
        for (RagSearchResult result : results) {
            unique.putIfAbsent(result.getId(), result);
        }
        return unique.values().stream().limit(limit).toList();
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
        if (request.getCandidateLimit() < 1 || request.getCandidateLimit() > 200) {
            throw new BusinessException("candidateLimit must be between 1 and 200.");
        }
        if (!Double.isFinite(request.getMinScore())) {
            throw new BusinessException("minScore must be a finite number.");
        }
        if (request.getMinScore() < -1.0d || request.getMinScore() > 1.0d) {
            throw new BusinessException("minScore must be between -1.0 and 1.0.");
        }
        RagRetrievalStrategy.from(request.getRetrievalStrategy());
    }

    private String normalizeProvider(String provider) {
        String normalized = trimToNull(provider);
        return normalized == null
                ? embeddingProperties.getDefaultProvider().trim().toUpperCase(Locale.ROOT)
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
