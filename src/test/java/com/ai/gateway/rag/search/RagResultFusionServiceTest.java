
package com.ai.gateway.rag.search;

import com.ai.gateway.rag.search.dto.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RagResultFusionServiceTest {

    private final RagResultFusionService service = new RagResultFusionService();

    @Test
    void shouldFuseResultsAndRemoveDuplicateIds() {
        UUID shared = UUID.randomUUID();
        RagSearchResult vector = result(shared, "vector", .9);
        RagSearchResult keyword = result(shared, "keyword", .8);
        RagSearchResult other = result(UUID.randomUUID(), "other", .7);

        List<RagSearchResult> fused = service.fuse(
                List.of(vector, other),
                List.of(keyword),
                10);

        assertEquals(2, fused.size());
        assertEquals(shared, fused.getFirst().getId());
    }

    private RagSearchResult result(UUID id, String content, double score) {
        return RagSearchResult.builder().id(id).content(content).similarity(score).build();
    }
}
