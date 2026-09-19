package com.ai.gateway.rag.augmentation;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.personal.rag.PersonalRagService;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.rag.search.RagSearchService;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
public class RagAugmentationServiceImpl implements RagAugmentationService {

    private static final int MAX_KNOWLEDGE_BASES = 10;

    private final RagSearchService ragSearchService;
    private final RagContextAssembler contextAssembler;
    private final RagContextOptimizer contextOptimizer;
    private final PersonalRagService personalRagService;

    @Autowired
    public RagAugmentationServiceImpl(
            RagSearchService ragSearchService,
            RagContextAssembler contextAssembler,
            RagContextOptimizer contextOptimizer,
            PersonalRagService personalRagService) {
        this.ragSearchService = ragSearchService;
        this.contextAssembler = contextAssembler;
        this.contextOptimizer = contextOptimizer;
        this.personalRagService = personalRagService;
    }

    /** Backward-compatible constructor for existing Phase 4 tests. */
    public RagAugmentationServiceImpl(
            RagSearchService ragSearchService,
            RagContextAssembler contextAssembler) {
        this(ragSearchService, contextAssembler, new RagContextOptimizer(), null);
    }

    /** Backward-compatible constructor for existing tests that inject the optimizer. */
    public RagAugmentationServiceImpl(
            RagSearchService ragSearchService,
            RagContextAssembler contextAssembler,
            RagContextOptimizer contextOptimizer) {
        this(ragSearchService, contextAssembler, contextOptimizer, null);
    }

    @Override
    public RagAugmentationResult augment(
            AuthenticationContext authentication,
            String query,
            RagRequest request) {
        if (authentication != null && authentication.isPersonalPrincipal()) {
            return augmentInternal(authentication, query, request);
        }
        return augment(authentication == null ? null : authentication.getTenantId(), query, request);
    }

    @Override
    public RagAugmentationResult augment(
            UUID tenantId,
            String query,
            RagRequest request) {
        return augmentInternal(tenantId, null, query, request);
    }

    private RagAugmentationResult augmentInternal(
            AuthenticationContext authentication,
            String query,
            RagRequest request) {
        return augmentInternal(authentication == null ? null : authentication.getTenantId(), authentication, query, request);
    }

    private RagAugmentationResult augmentInternal(
            UUID tenantId,
            AuthenticationContext authentication,
            String query,
            RagRequest request) {

        if (request == null || !request.isEnabled()) {
            return RagAugmentationResult.builder()
                    .augmentedPrompt(query)
                    .chunks(List.of())
                    .knowledgeBaseCount(0)
                    .retrievedCount(0)
                    .selectedCount(0)
                    .deduplicatedCount(0)
                    .droppedCount(0)
                    .truncatedCount(0)
                    .estimatedContextTokens(0)
                    .contextTokenBudget(0)
                    .build();
        }

        validate(tenantId, authentication, query, request);

        List<RagContextChunk> candidates = new ArrayList<>();
        Set<UUID> knowledgeBaseIds = new LinkedHashSet<>();
        for (String knowledgeBaseId : request.getKnowledgeBaseIds()) {
            knowledgeBaseIds.add(parseKnowledgeBaseId(knowledgeBaseId));
        }

        for (UUID kbId : knowledgeBaseIds) {

            RagSearchRequest searchRequest = RagSearchRequest.builder()
                    .query(query.trim())
                    .topK(request.getTopK())
                    .minScore(request.getMinScore())
                    .retrievalStrategy(request.getRetrievalStrategy())
                    .queryTransformation(request.isQueryTransformation())
                    .candidateLimit(request.getCandidateLimit())
                    .build();

            RagSearchResponse response;
            if (authentication != null && authentication.isPersonalPrincipal()) {
                if (personalRagService == null) {
                    throw new BusinessException("Personal RAG service is not available.");
                }
                // Personal RAG is account-scoped. PersonalRagService authorizes
                // the account and constrains the KB/chunks by personal_account_id.
                response = personalRagService.search(authentication, kbId, searchRequest);
            } else {
                response = ragSearchService.search(tenantId, kbId, searchRequest);
            }

            for (RagSearchResult result : response.getResults()) {
                candidates.add(RagContextChunk.builder()
                        .id(result.getId())
                        .documentId(result.getDocumentId())
                        .knowledgeBaseId(kbId)
                        .fileName(result.getFileName())
                        .chunkIndex(result.getChunkIndex())
                        .recordId(result.getRecordId())
                        .sectionId(result.getSectionId())
                        .chunkId(result.getChunkId())
                        .content(result.getContent())
                        .metadataJson(result.getMetadataJson())
                        .similarity(result.getSimilarity())
                        .build());
            }
        }

        candidates.sort(Comparator
                .comparingDouble(RagContextChunk::getSimilarity)
                .reversed()
                .thenComparing(RagContextChunk::getKnowledgeBaseId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RagContextChunk::getDocumentId,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(RagContextChunk::getChunkIndex)
                .thenComparing(RagContextChunk::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        UUID requestId = requestId();

        long optimizationStart = System.nanoTime();
        RagContextOptimizationResult optimization = contextOptimizer.optimizeDetailed(
                candidates,
                request.getContextTokenBudget(),
                request.getTopK());
        logStage("RAG_CONTEXT_OPTIMIZATION", requestId, optimizationStart,
                "selected=" + optimization.getSelectedChunks().size()
                        + " estimatedTokens=" + optimization.getEstimatedContextTokens());

        List<RagContextChunk> selected = optimization.getSelectedChunks();

        long assemblyStart = System.nanoTime();
        String augmentedPrompt = contextAssembler.augment(query.trim(), selected);
        logStage("RAG_CONTEXT_ASSEMBLY", requestId, assemblyStart,
                "selected=" + selected.size());

        return RagAugmentationResult.builder()
                .augmentedPrompt(augmentedPrompt)
                .chunks(selected)
                .knowledgeBaseCount(knowledgeBaseIds.size())
                .retrievedCount(candidates.size())
                .selectedCount(selected.size())
                .deduplicatedCount(optimization.getDeduplicatedCount())
                .droppedCount(optimization.getDroppedCount())
                .truncatedCount(optimization.getTruncatedCount())
                .estimatedContextTokens(optimization.getEstimatedContextTokens())
                .contextTokenBudget(optimization.getContextTokenBudget())
                .build();
    }

    private void logStage(String stage, UUID requestId, long started, String outcome) {
        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        LoggerFactory.getLogger("com.ai.gateway.performance")
                .info("event=RAG_STAGE stage={} requestId={} durationMs={} outcome={}",
                        stage, requestId, durationMs, outcome);
    }

    private UUID requestId() {
        String value = MDC.get("requestId");
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void validate(UUID tenantId, AuthenticationContext authentication, String query, RagRequest request) {
        boolean personal = authentication != null && authentication.isPersonalPrincipal();
        if (!personal && tenantId == null) {
            throw new BusinessException("Tenant ID is required for Business RAG augmentation.");
        }
        if (personal && authentication.getPersonalAccountId() == null) {
            throw new BusinessException("Personal account context is required for RAG augmentation.");
        }
        if (query == null || query.isBlank()) {
            throw new BusinessException("RAG query is required when RAG is enabled.");
        }
        if (query.length() > 10000) {
            throw new BusinessException("RAG query must not exceed 10000 characters.");
        }
        if (request.getKnowledgeBaseIds() == null || request.getKnowledgeBaseIds().isEmpty()) {
            throw new BusinessException("At least one knowledge base is required when RAG is enabled.");
        }
        if (request.getKnowledgeBaseIds().size() > MAX_KNOWLEDGE_BASES) {
            throw new BusinessException("A maximum of " + MAX_KNOWLEDGE_BASES + " knowledge bases may be used per request.");
        }
        if (request.getTopK() < 1 || request.getTopK() > 100) {
            throw new BusinessException("RAG topK must be between 1 and 100.");
        }
        if (!Double.isFinite(request.getMinScore())
                || request.getMinScore() < -1.0d
                || request.getMinScore() > 1.0d) {
            throw new BusinessException("RAG minScore must be between -1.0 and 1.0.");
        }

        String strategy = request.getRetrievalStrategy();
        if (strategy == null || strategy.isBlank()) {
            throw new BusinessException("RAG retrievalStrategy is required when RAG is enabled.");
        }
        try {
            com.ai.gateway.rag.search.RagRetrievalStrategy.from(strategy);
        } catch (BusinessException ex) {
            throw ex;
        }
        if (request.getCandidateLimit() < 1 || request.getCandidateLimit() > 200) {
            throw new BusinessException("RAG candidateLimit must be between 1 and 200.");
        }
        if (request.getContextTokenBudget() < 256 || request.getContextTokenBudget() > 32768) {
            throw new BusinessException("RAG contextTokenBudget must be between 256 and 32768.");
        }
    }

    private UUID parseKnowledgeBaseId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("Knowledge base ID cannot be blank.");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid knowledge base ID: " + value);
        }
    }
}
