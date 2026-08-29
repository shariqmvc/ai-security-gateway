package com.ai.gateway.core.contract;

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
