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

    /** Provider requested by the caller. */
    private String requestedProvider;

    /** Model requested by the caller. */
    private String requestedModel;

    /** Provider/model that actually generated the response. */
    private String provider;
    private String model;

    /** Non-null only when execution failed over from the requested provider. */
    private String failoverReason;

    private String finishReason;

    private Integer inputTokens;
    private Integer outputTokens;
    private Integer totalTokens;

    /** Phase 4 RAG execution metadata; null when RAG is disabled. */
    private RagMetadata rag;

}
