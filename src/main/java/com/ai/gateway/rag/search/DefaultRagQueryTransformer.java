package com.ai.gateway.rag.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultRagQueryTransformer implements RagQueryTransformer {

    @Override
    public List<String> transform(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        // Deterministic expansion: preserve the original query and add a
        // punctuation-free form for embedding/keyword engines that tokenize
        // punctuation differently. This is deliberately provider-neutral.
        String simplified = normalized
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (simplified.isBlank() || simplified.equalsIgnoreCase(normalized)) {
            return List.of(normalized);
        }
        return List.of(normalized, simplified);
    }
}
