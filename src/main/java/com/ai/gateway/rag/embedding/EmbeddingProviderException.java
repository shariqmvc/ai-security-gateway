package com.ai.gateway.rag.embedding;

public class EmbeddingProviderException extends RuntimeException {
    public EmbeddingProviderException(String message) { super(message); }
    public EmbeddingProviderException(String message, Throwable cause) { super(message, cause); }
}
