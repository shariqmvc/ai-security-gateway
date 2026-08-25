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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.slf4j.MDC;

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
    private final TransactionTemplate transactionTemplate;

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
            TenantSchemaRoutingService tenantSchemaRoutingService,
            PlatformTransactionManager transactionManager) {
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
        this.transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
    }

    /**
     * Backward-compatible constructor for the Phase 4 test suite and integrations
     * that provide the full retrieval collaborators but do not manage transactions.
     */
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
        this(
                knowledgeBaseRepository,
                vectorSearchRepository,
                keywordSearchRepository,
                fusionService,
                reranker,
                queryTransformer,
                providerFactory,
                embeddingProperties,
                tenantAccessGuard,
                tenantSchemaRoutingService,
                null);
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
                tenantSchemaRoutingService,
                null);
    }

    @Override
    public RagSearchResponse search(
            UUID tenantId,
            UUID knowledgeBaseId,
            RagSearchRequest request) {

        long totalStart = System.nanoTime();
        UUID requestId = requestId();

        long stage = System.nanoTime();
        tenantAccessGuard.requireAccess(tenantId);
        validateRequest(request);
        logStage("RAG_ACCESS_VALIDATION", requestId, stage, "SUCCESS");

        /*
         * Database transactions are deliberately limited to database work.
         * Tenant schema routing uses SET LOCAL, so DB access must remain inside
         * an active transaction. Provider embedding HTTP calls do not need one.
         * This prevents a slow embedding provider (for example Ollama) from
         * holding a database connection for the duration of the HTTP call.
         */
        stage = System.nanoTime();
        KnowledgeBase knowledgeBase = loadKnowledgeBase(tenantId, knowledgeBaseId);
        logStage("RAG_KB_RESOLUTION", requestId, stage, "SUCCESS");

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

        stage = System.nanoTime();
        List<String> queries = request.isQueryTransformation()
                ? queryTransformer.transform(query)
                : List.of(query);
        logStage("RAG_QUERY_TRANSFORMATION", requestId, stage,
                "queries=" + queries.size());

        String providerName = null;
        String model = null;
        int dimension = 0;
        List<VectorSearchInput> vectorInputs = new ArrayList<>();

        if (strategy != RagRetrievalStrategy.KEYWORD) {
            stage = System.nanoTime();
            providerName = normalizeProvider(knowledgeBase.getEmbeddingProvider());
            EmbeddingProvider provider = providerFactory.get(providerName);
            model = trimToNull(knowledgeBase.getEmbeddingModel());
            if (model == null) {
                model = provider.defaultModel();
            }

            for (String transformedQuery : queries) {
                EmbeddingVector embedding = singleEmbedding(provider, transformedQuery, model);
                dimension = embedding.dimension();
                vectorInputs.add(new VectorSearchInput(
                        EmbeddingVectorFormatter.toPgVector(embedding),
                        dimension));
            }

            logStage("RAG_EMBEDDING", requestId, stage,
                    "provider=" + providerName
                            + " model=" + model
                            + " queries=" + queries.size());
        }

        stage = System.nanoTime();
        RetrievalResults retrieval = executeDatabaseRetrieval(
                tenantId,
                knowledgeBaseId,
                strategy,
                request,
                queries,
                providerName,
                model,
                vectorInputs,
                requestId);
        logStage("RAG_DATABASE_RETRIEVAL", requestId, stage,
                "vector=" + retrieval.vectorResults().size()
                        + " keyword=" + retrieval.keywordResults().size());

        List<RagSearchResult> results;
        if (strategy == RagRetrievalStrategy.VECTOR) {
            results = retrieval.vectorResults();
        } else if (strategy == RagRetrievalStrategy.KEYWORD) {
            results = retrieval.keywordResults();
        } else {
            stage = System.nanoTime();
            results = fusionService.fuse(
                    retrieval.vectorResults(),
                    retrieval.keywordResults(),
                    request.getCandidateLimit());
            logStage("RAG_FUSION", requestId, stage,
                    "candidates=" + results.size());

            if (strategy == RagRetrievalStrategy.HYBRID_RERANKED) {
                stage = System.nanoTime();
                results = reranker.rerank(
                        query, results, request.getCandidateLimit());
                logStage("RAG_RERANK", requestId, stage,
                        "candidates=" + results.size());
            }
        }

        stage = System.nanoTime();
        results = filterByMinScore(results, request.getMinScore())
                .stream()
                .limit(request.getTopK())
                .toList();
        logStage("RAG_FINAL_FILTER", requestId, stage,
                "selected=" + results.size());

        logStage("RAG_TOTAL", requestId, totalStart,
                "strategy=" + strategy.name());

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

    private KnowledgeBase loadKnowledgeBase(UUID tenantId, UUID knowledgeBaseId) {
        if (transactionTemplate == null) {
            tenantSchemaRoutingService.useTenantSchema(tenantId);
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .orElseThrow(() -> new KnowledgeBaseNotFoundException(knowledgeBaseId));
        }

        return transactionTemplate.execute(status -> {
            tenantSchemaRoutingService.useTenantSchema(tenantId);
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .orElseThrow(() -> new KnowledgeBaseNotFoundException(knowledgeBaseId));
        });
    }

    private RetrievalResults executeDatabaseRetrieval(
            UUID tenantId,
            UUID knowledgeBaseId,
            RagRetrievalStrategy strategy,
            RagSearchRequest request,
            List<String> queries,
            String providerName,
            String model,
            List<VectorSearchInput> vectorInputs,
            UUID requestId) {

        if (transactionTemplate == null) {
            tenantSchemaRoutingService.useTenantSchema(tenantId);
            return executeRetrievalQueries(
                    knowledgeBaseId,
                    strategy,
                    request,
                    queries,
                    providerName,
                    model,
                    vectorInputs,
                    requestId);
        }

        return transactionTemplate.execute(status -> {
            tenantSchemaRoutingService.useTenantSchema(tenantId);
            return executeRetrievalQueries(
                    knowledgeBaseId,
                    strategy,
                    request,
                    queries,
                    providerName,
                    model,
                    vectorInputs,
                    requestId);
        });
    }

    private RetrievalResults executeRetrievalQueries(
            UUID knowledgeBaseId,
            RagRetrievalStrategy strategy,
            RagSearchRequest request,
            List<String> queries,
            String providerName,
            String model,
            List<VectorSearchInput> vectorInputs,
            UUID requestId) {

        List<RagSearchResult> vectorResults = new ArrayList<>();

        if (strategy != RagRetrievalStrategy.KEYWORD) {
            long vectorStart = System.nanoTime();
            for (VectorSearchInput input : vectorInputs) {
                vectorResults.addAll(vectorSearchRepository.search(
                        knowledgeBaseId,
                        input.queryVector(),
                        providerName,
                        model,
                        input.dimension(),
                        request.getCandidateLimit(),
                        request.getMinScore()));
            }
            vectorResults = deduplicate(
                    vectorResults, request.getCandidateLimit());
            logStage("RAG_VECTOR_SEARCH", requestId, vectorStart,
                    "results=" + vectorResults.size());
        }

        List<RagSearchResult> keywordResults = List.of();
        if (strategy == RagRetrievalStrategy.KEYWORD
                || strategy == RagRetrievalStrategy.HYBRID
                || strategy == RagRetrievalStrategy.HYBRID_RERANKED) {

            long keywordStart = System.nanoTime();
            List<RagSearchResult> all = new ArrayList<>();
            for (String transformedQuery : queries) {
                all.addAll(keywordSearchRepository.search(
                        knowledgeBaseId,
                        transformedQuery,
                        request.getCandidateLimit()));
            }
            keywordResults = deduplicate(
                    all, request.getCandidateLimit());
            logStage("RAG_KEYWORD_SEARCH", requestId, keywordStart,
                    "results=" + keywordResults.size());
        }

        return new RetrievalResults(vectorResults, keywordResults);
    }

    private void logStage(
            String stage,
            UUID requestId,
            long started,
            String outcome) {

        long durationMs =
                (System.nanoTime() - started) / 1_000_000L;

        org.slf4j.LoggerFactory
                .getLogger("com.ai.gateway.performance")
                .info(
                        "event=RAG_STAGE stage={} requestId={} durationMs={} outcome={}",
                        stage,
                        requestId,
                        durationMs,
                        outcome);
    }

    private UUID requestId() {
        String value = MDC.get("requestId");
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record RetrievalResults(
            List<RagSearchResult> vectorResults,
            List<RagSearchResult> keywordResults) {}

    private record VectorSearchInput(
            String queryVector,
            int dimension) {}

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
