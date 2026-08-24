package com.ai.gateway.rag.search;

public enum RagRetrievalStrategy {
    VECTOR,
    KEYWORD,
    HYBRID,
    HYBRID_RERANKED;

    public static RagRetrievalStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return VECTOR;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.ai.gateway.exception.BusinessException(
                    "Unsupported RAG retrievalStrategy: " + value
                            + ". Supported values: VECTOR, KEYWORD, HYBRID, HYBRID_RERANKED.");
        }
    }
}
