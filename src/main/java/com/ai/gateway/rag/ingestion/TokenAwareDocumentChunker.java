package com.ai.gateway.rag.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic token-budget chunker used before provider-specific embedding.
 * Token counts are conservative estimates (roughly four characters per token)
 * so Phase 2 does not become coupled to a particular embedding tokenizer.
 */
@Component
@RequiredArgsConstructor
public class TokenAwareDocumentChunker implements DocumentChunker {

    private static final int APPROX_CHARS_PER_TOKEN = 4;

    private final RagIngestionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public List<DocumentChunk> chunk(String normalizedText) {
        int maxTokens = properties.getChunkSizeTokens();
        int overlapTokens = properties.getChunkOverlapTokens();

        if (maxTokens <= 0) {
            throw new IllegalArgumentException("RAG chunk size must be greater than zero.");
        }
        if (overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException(
                    "RAG chunk overlap must be >= 0 and smaller than chunk size.");
        }

        List<String> words = List.of(normalizedText.trim().split("\\s+"));
        List<DocumentChunk> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentTokens = 0;
        int index = 0;

        for (String word : words) {
            int wordTokens = estimateTokens(word);

            if (wordTokens > maxTokens) {
                if (!current.isEmpty()) {
                    chunks.add(buildChunk(index++, current));
                    enforceMaxChunks(chunks.size());
                    current = new ArrayList<>();
                    currentTokens = 0;
                }

                for (int start = 0; start < word.length(); start += maxTokens * APPROX_CHARS_PER_TOKEN) {
                    int end = Math.min(
                            word.length(),
                            start + maxTokens * APPROX_CHARS_PER_TOKEN);
                    String part = word.substring(start, end);
                    chunks.add(buildChunk(index++, List.of(part)));
                    enforceMaxChunks(chunks.size());
                }
                continue;
            }

            if (!current.isEmpty()
                    && currentTokens + wordTokens > maxTokens) {
                chunks.add(buildChunk(index++, current));
                enforceMaxChunks(chunks.size());

                List<String> overlap = takeOverlapFromEnd(current, overlapTokens);
                current = new ArrayList<>(overlap);
                currentTokens = estimateTokens(current);
            }

            current.add(word);
            currentTokens += wordTokens;
        }

        if (!current.isEmpty()) {
            chunks.add(buildChunk(index, current));
        }

        return chunks;
    }

    private DocumentChunk buildChunk(int index, List<String> words) {
        String content = String.join(" ", words).trim();
        int tokenCount = estimateTokens(content);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("chunkIndex", index);
        metadata.put("estimatedTokenCount", tokenCount);
        metadata.put("chunkingStrategy", "TOKEN_AWARE");

        try {
            return new DocumentChunk(
                    index,
                    content,
                    tokenCount,
                    objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException ex) {
            throw new DocumentIngestionException(
                    "Unable to serialize RAG chunk metadata.", ex);
        }
    }

    private void enforceMaxChunks(int currentChunkCount) {
        if (currentChunkCount > properties.getMaxChunks()) {
            throw new DocumentIngestionException(
                    "Document exceeds the maximum supported chunk count: "
                            + properties.getMaxChunks());
        }
    }

    private List<String> takeOverlapFromEnd(List<String> words, int overlapTokens) {
        if (overlapTokens == 0) {
            return List.of();
        }

        List<String> overlap = new ArrayList<>();
        int tokens = 0;
        for (int i = words.size() - 1; i >= 0; i--) {
            int candidate = estimateTokens(words.get(i));
            if (tokens + candidate > overlapTokens) {
                break;
            }
            overlap.add(0, words.get(i));
            tokens += candidate;
        }
        return overlap;
    }

    private int estimateTokens(List<String> words) {
        return words.stream()
                .mapToInt(this::estimateTokens)
                .sum();
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1,
                (value.length() + APPROX_CHARS_PER_TOKEN - 1)
                        / APPROX_CHARS_PER_TOKEN);
    }
}
