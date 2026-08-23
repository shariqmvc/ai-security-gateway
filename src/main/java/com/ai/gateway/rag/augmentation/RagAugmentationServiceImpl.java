package com.ai.gateway.rag.augmentation;

import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.rag.search.RagSearchService;
import com.ai.gateway.rag.search.dto.RagSearchRequest;
import com.ai.gateway.rag.search.dto.RagSearchResponse;
import com.ai.gateway.rag.search.dto.RagSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RagAugmentationServiceImpl implements RagAugmentationService {

    private static final int MAX_KNOWLEDGE_BASES = 10;

    private final RagSearchService ragSearchService;
    private final RagContextAssembler contextAssembler;

    @Override
    public RagAugmentationResult augment(
            UUID tenantId,
            String query,
            RagRequest request) {

        if (request == null || !request.isEnabled()) {
            return RagAugmentationResult.builder()
                    .augmentedPrompt(query)
                    .chunks(List.of())
                    .knowledgeBaseCount(0)
                    .retrievedCount(0)
                    .selectedCount(0)
                    .build();
        }

        validate(tenantId, query, request);

        List<RagContextChunk> candidates = new ArrayList<>();
        Set<UUID> knowledgeBaseIds = new LinkedHashSet<>();
        for (String knowledgeBaseId : request.getKnowledgeBaseIds()) {
            knowledgeBaseIds.add(parseKnowledgeBaseId(knowledgeBaseId));
        }

        for (UUID kbId : knowledgeBaseIds) {

            RagSearchResponse response = ragSearchService.search(
                    tenantId,
                    kbId,
                    RagSearchRequest.builder()
                            .query(query.trim())
                            .topK(request.getTopK())
                            .minScore(request.getMinScore())
                            .build());

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

        List<RagContextChunk> selected = candidates.stream()
                .limit(request.getTopK())
                .toList();

        return RagAugmentationResult.builder()
                .augmentedPrompt(contextAssembler.augment(query.trim(), selected))
                .chunks(selected)
                .knowledgeBaseCount(knowledgeBaseIds.size())
                .retrievedCount(candidates.size())
                .selectedCount(selected.size())
                .build();
    }

    private void validate(UUID tenantId, String query, RagRequest request) {
        if (tenantId == null) {
            throw new BusinessException("Tenant ID is required for RAG augmentation.");
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
        if (strategy == null || strategy.isBlank()
                || !"VECTOR".equalsIgnoreCase(strategy.trim())) {
            throw new BusinessException(
                    "Unsupported RAG retrievalStrategy: " + strategy
                            + ". Phase 4 currently supports VECTOR only.");
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
