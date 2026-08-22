package com.ai.gateway.rag.embedding;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class EmbeddingProviderFactory {
    private final List<EmbeddingProvider> providers;

    public EmbeddingProvider get(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new EmbeddingProviderException("Knowledge base embedding provider is not configured.");
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        return providers.stream()
                .filter(p -> p.provider().equalsIgnoreCase(normalized))
                .findFirst()
                .orElseThrow(() -> new EmbeddingProviderException(
                        "Unsupported embedding provider: " + provider));
    }
}
