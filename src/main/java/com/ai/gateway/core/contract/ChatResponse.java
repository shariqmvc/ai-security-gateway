package com.ai.gateway.core.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    private UUID requestId;

    private String response;

    private String finishReason;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    /** Phase 4 RAG execution metadata; null when RAG is disabled. */
    private RagMetadata rag;

}
