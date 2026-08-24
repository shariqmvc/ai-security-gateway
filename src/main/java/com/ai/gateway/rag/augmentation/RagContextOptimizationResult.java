package com.ai.gateway.rag.augmentation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class RagContextOptimizationResult {
    List<RagContextChunk> selectedChunks;
    int candidateCount;
    int deduplicatedCount;
    int droppedCount;
    int truncatedCount;
    int estimatedContextTokens;
    int contextTokenBudget;
}
