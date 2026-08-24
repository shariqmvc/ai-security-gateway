package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class TokenOverlapRagReranker implements RagReranker {

    @Override
    public List<RagSearchResult> rerank(
            String query, List<RagSearchResult> candidates, int limit) {

        Set<String> queryTokens = tokens(query);
        return candidates.stream()
                .sorted(
                        Comparator.<RagSearchResult>comparingDouble(
                                        result -> rerankScore(queryTokens, result))
                                .reversed()
                                .thenComparing(RagSearchResult::getId,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    private double rerankScore(Set<String> queryTokens, RagSearchResult result) {
        if (queryTokens.isEmpty()) return result.getSimilarity();
        Set<String> contentTokens = tokens(result.getContent());
        long overlap = queryTokens.stream().filter(contentTokens::contains).count();
        double lexical = (double) overlap / queryTokens.size();
        return 0.70d * lexical + 0.30d * Math.max(0d, Math.min(1d, result.getSimilarity()));
    }

    private Set<String> tokens(String value) {
        if (value == null) return Set.of();
        return Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                        .split("\\s+"))
                .filter(s -> s.length() >= 2)
                .collect(Collectors.toSet());
    }
}
