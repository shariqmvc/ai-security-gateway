package com.ai.gateway.rag.augmentation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagContextOptimizer {

    private static final int CHARS_PER_APPROX_TOKEN = 4;

    public List<RagContextChunk> optimize(
            List<RagContextChunk> chunks,
            int maxContextTokens,
            int maxChunks) {

        int tokenBudget = Math.max(256, maxContextTokens);
        int chunkLimit = Math.max(1, Math.min(100, maxChunks));
        int used = 0;
        List<RagContextChunk> selected = new ArrayList<>();

        for (RagContextChunk chunk : chunks) {
            if (selected.size() >= chunkLimit) break;
            int tokens = estimateTokens(chunk.getContent());
            if (selected.isEmpty() || used + tokens <= tokenBudget) {
                selected.add(chunk);
                used += tokens;
            }
        }
        return List.copyOf(selected);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 1;
        return Math.max(1, (text.length() + CHARS_PER_APPROX_TOKEN - 1)
                / CHARS_PER_APPROX_TOKEN);
    }
}
