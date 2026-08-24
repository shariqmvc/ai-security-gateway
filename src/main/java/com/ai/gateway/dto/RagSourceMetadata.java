package com.ai.gateway.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RagSourceMetadata {
    UUID knowledgeBaseId;
    UUID documentId;
    String fileName;
    int chunkIndex;
    double similarity;
}
