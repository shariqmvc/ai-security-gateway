package com.ai.gateway.rag.search.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RagSearchResult {
    UUID id;
    UUID documentId;
    String fileName;
    int chunkIndex;
    String content;
    String metadataJson;
    double similarity;
}
