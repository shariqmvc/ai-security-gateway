package com.ai.gateway.rag.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagRequest {

    @Builder.Default
    private boolean enabled = false;

    @Builder.Default
    private List<String> knowledgeBaseIds = Collections.emptyList();

    @Min(1)
    @Max(100)
    @Builder.Default
    private int topK = 5;

    @Builder.Default
    private String retrievalStrategy = "HYBRID";
}
