package com.ai.gateway.rag.augmentation;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RagContextOptimizer {

    private final RagContextTokenEstimator tokenEstimator;

    public RagContextOptimizer(RagContextTokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    /** Backward-compatible constructor for existing tests/integrations. */
    public RagContextOptimizer() {
        this(new RagContextTokenEstimator());
    }

    public List<RagContextChunk> optimize(
            List<RagContextChunk> chunks,
            int maxContextTokens,
            int maxChunks) {
        return optimizeDetailed(chunks, maxContextTokens, maxChunks).getSelectedChunks();
    }

    public RagContextOptimizationResult optimizeDetailed(
            List<RagContextChunk> chunks,
            int maxContextTokens,
            int maxChunks) {

        List<RagContextChunk> input = chunks == null ? List.of() : chunks;
        int budget = Math.max(256, Math.min(32768, maxContextTokens));
        int limit = Math.max(1, Math.min(100, maxChunks));

        List<RagContextChunk> unique = deduplicate(input);
        int deduplicated = input.size() - unique.size();
        List<RagContextChunk> selected = new ArrayList<>();
        int truncated = 0;
        int estimated = tokenEstimator.estimateContextTokens(List.of());

        for (RagContextChunk candidate : unique) {
            if (selected.size() >= limit) break;

            List<RagContextChunk> tentative = new ArrayList<>(selected);
            tentative.add(candidate);
            int candidateTokens = tokenEstimator.estimateContextTokens(tentative);

            if (candidateTokens <= budget) {
                selected.add(candidate);
                estimated = candidateTokens;
                continue;
            }

            int remaining = budget - tokenEstimator.estimateContextTokens(selected);
            if (remaining <= 0) break;

            int wrapperTokens = tokenEstimator.estimateChunkTokens(
                    candidate.toBuilder().content("").build());
            int contentBudget = remaining - wrapperTokens;
            if (contentBudget <= 8) break;

            String truncatedContent = tokenEstimator.truncateToTokenBudget(
                    candidate.getContent(), contentBudget);
            RagContextChunk truncatedChunk = candidate.toBuilder()
                    .content(truncatedContent)
                    .build();

            tentative = new ArrayList<>(selected);
            tentative.add(truncatedChunk);
            int truncatedTokens = tokenEstimator.estimateContextTokens(tentative);
            if (truncatedTokens <= budget) {
                selected.add(truncatedChunk);
                estimated = truncatedTokens;
                truncated++;
            }
            break;
        }

        return RagContextOptimizationResult.builder()
                .selectedChunks(List.copyOf(selected))
                .candidateCount(input.size())
                .deduplicatedCount(deduplicated)
                .droppedCount(Math.max(0, unique.size() - selected.size()))
                .truncatedCount(truncated)
                .estimatedContextTokens(estimated)
                .contextTokenBudget(budget)
                .build();
    }

    private List<RagContextChunk> deduplicate(List<RagContextChunk> chunks) {
        List<RagContextChunk> unique = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();

        for (RagContextChunk candidate : chunks) {
            if (candidate == null) continue;
            if (candidate.getId() != null && !ids.add(candidate.getId())) continue;

            if (isNearDuplicate(candidate, unique)) continue;
            unique.add(candidate);
        }
        return unique;
    }

    private boolean isNearDuplicate(RagContextChunk candidate, List<RagContextChunk> existing) {
        Set<String> candidateTokens = tokens(candidate.getContent());
        if (candidateTokens.isEmpty()) return false;

        for (RagContextChunk prior : existing) {
            Set<String> priorTokens = tokens(prior.getContent());
            if (priorTokens.isEmpty()) continue;

            int intersection = 0;
            Set<String> smaller = candidateTokens.size() <= priorTokens.size()
                    ? candidateTokens : priorTokens;
            Set<String> larger = smaller == candidateTokens ? priorTokens : candidateTokens;
            for (String token : smaller) {
                if (larger.contains(token)) intersection++;
            }

            int union = candidateTokens.size() + priorTokens.size() - intersection;
            double jaccard = union == 0 ? 0.0d : (double) intersection / union;
            if (jaccard >= 0.90d) return true;
        }
        return false;
    }

    private Set<String> tokens(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(
                value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                        .trim()
                        .split("\\s+")));
    }
}
