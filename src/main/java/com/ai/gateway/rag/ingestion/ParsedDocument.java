package com.ai.gateway.rag.ingestion;

public record ParsedDocument(
        String text,
        String detectedContentType) {
}
