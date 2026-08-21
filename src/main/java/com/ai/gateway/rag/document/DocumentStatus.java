package com.ai.gateway.rag.document;

public enum DocumentStatus {
    REGISTERED,
    PROCESSING,
    READY_FOR_EMBEDDING,
    INDEXED,
    FAILED,
    DELETED
}
