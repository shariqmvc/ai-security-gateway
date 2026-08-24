
package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenOverlapRagRerankerTest {

    @Test
    void shouldPreferContentWithMoreQueryTokenOverlap() {
        TokenOverlapRagReranker reranker = new TokenOverlapRagReranker();
        RagSearchResult relevant = result("pgvector provides vector search for postgres", .5);
        RagSearchResult weak = result("database operations and storage", .9);

        List<RagSearchResult> ranked = reranker.rerank(
                "pgvector vector search",
                List.of(weak, relevant),
                2);

        assertEquals(relevant.getId(), ranked.getFirst().getId());
    }

    private RagSearchResult result(String content, double score) {
        return RagSearchResult.builder()
                .id(UUID.randomUUID()).content(content).similarity(score).build();
    }
}
