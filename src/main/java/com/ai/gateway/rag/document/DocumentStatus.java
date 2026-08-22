package com.ai.gateway.rag.document;

public enum DocumentStatus {
    REGISTERED,
    PROCESSING,
    READY_FOR_EMBEDDING,
    EMBEDDING,
    INDEXED,
    FAILED,
    DELETED
}
