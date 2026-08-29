package com.ai.gateway.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RagMetadata {
    boolean enabled;
    String retrievalStrategy;
    int knowledgeBaseCount;
    int retrievedCount;
    int selectedCount;
    int deduplicatedCount;
    int droppedCount;
    int truncatedCount;
    int estimatedContextTokens;
    int contextTokenBudget;
    List<RagSourceMetadata> sources;
}
