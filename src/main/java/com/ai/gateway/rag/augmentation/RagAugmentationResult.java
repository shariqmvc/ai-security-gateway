package com.ai.gateway.rag.augmentation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagAugmentationResult {
    String augmentedPrompt;
    List<RagContextChunk> chunks;
    int knowledgeBaseCount;
    int retrievedCount;
    int selectedCount;
}
