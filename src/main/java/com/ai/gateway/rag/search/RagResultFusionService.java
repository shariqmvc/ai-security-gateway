package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RagResultFusionService {

    public List<RagSearchResult> fuse(
            List<RagSearchResult> vector,
            List<RagSearchResult> keyword,
            int limit) {

        Map<UUID, Candidate> merged = new LinkedHashMap<>();
        add(merged, vector, true);
        add(merged, keyword, false);

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(c -> c.result().getId()))
                .limit(limit)
                .map(Candidate::result)
                .toList();
    }

    private void add(Map<UUID, Candidate> merged, List<RagSearchResult> results, boolean vector) {
        if (results == null || results.isEmpty()) return;

        double max = results.stream()
                .mapToDouble(RagSearchResult::getSimilarity)
                .max()
                .orElse(1.0d);
        if (max <= 0) max = 1.0d;

        for (int i = 0; i < results.size(); i++) {
            RagSearchResult result = results.get(i);
            double normalized = Math.max(0d, result.getSimilarity() / max);
            // Reciprocal-rank contribution is robust when vector and lexical
            // score distributions are not comparable.
            double rrf = 1.0d / (60.0d + i + 1.0d);
            double contribution = vector
                    ? 0.70d * normalized + 0.30d * rrf
                    : 0.30d * normalized + 0.70d * rrf;

            Candidate current = merged.get(result.getId());
            if (current == null) {
                merged.put(result.getId(), new Candidate(result, contribution));
            } else {
                merged.put(result.getId(),
                        new Candidate(current.result(), current.score() + contribution));
            }
        }
    }

    private record Candidate(RagSearchResult result, double score) {}
}
