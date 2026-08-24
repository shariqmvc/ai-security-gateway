package com.ai.gateway.rag.augmentation;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic, dependency-free token estimator for retrieved context.
 * It deliberately overestimates source-wrapper overhead so the assembled
 * context stays within the configured budget.
 */
@Component
public class RagContextTokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int HEADER_CHARS = 220;
    private static final int SOURCE_OVERHEAD_CHARS = 180;

    public int estimateContextTokens(List<RagContextChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return estimateCharacters(HEADER_CHARS);
        }

        int chars = HEADER_CHARS;
        for (RagContextChunk chunk : chunks) {
            chars += SOURCE_OVERHEAD_CHARS;
            chars += chunk == null || chunk.getContent() == null
                    ? 0
                    : chunk.getContent().length();
        }
        return estimateCharacters(chars);
    }

    public int estimateChunkTokens(RagContextChunk chunk) {
        if (chunk == null) return 1;
        int chars = SOURCE_OVERHEAD_CHARS
                + (chunk.getContent() == null ? 0 : chunk.getContent().length());
        return estimateCharacters(chars);
    }

    public int estimateTextTokens(String text) {
        return estimateCharacters(text == null ? 0 : text.length());
    }

    public String truncateToTokenBudget(String text, int tokenBudget) {
        if (text == null || text.isEmpty()) return "";
        int maxChars = Math.max(1, tokenBudget) * CHARS_PER_TOKEN;
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private int estimateCharacters(int chars) {
        return Math.max(1, (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN);
    }
}
