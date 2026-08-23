package com.ai.gateway.rag.augmentation;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class RagContextChunk {
    UUID id;
    UUID documentId;
    UUID knowledgeBaseId;
    String fileName;
    int chunkIndex;
    String recordId;
    String sectionId;
    String chunkId;
    String content;
    String metadataJson;
    double similarity;
}
