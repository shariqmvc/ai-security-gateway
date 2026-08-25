package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import lombok.*;

import java.util.Collections;
import java.util.Set;
import java.math.BigDecimal;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.multimodal.MediaContent;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

   @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
    private Provider provider;

   /** Optional multimodal input. Text remains in prompt for backward compatibility. */
   @Builder.Default
   @Valid
   @Size(max = 8, message = "A maximum of 8 media items is allowed per request.")
   private java.util.List<MediaContent> media = java.util.Collections.emptyList();
    private String model;

   /** Optional user-requested capabilities used by intelligent routing. */
   @Builder.Default
   private Set<String> requiredCapabilities = Collections.emptySet();

   /** Opt-in Unity / Extensive Research request flag. */
   private boolean extensiveResearch;

   /** Optional Unity execution role, e.g. research-synthesis. */
   private String executionRole;

   /** Optional routing priority: BALANCED, COST, LATENCY, RELIABILITY. */
   private String routingPriority;

   /** Optional explicit multi-objective optimization profile. */
   private String routingOptimizationProfile;

   /** Terminal routing selection mode: SINGLE, TOP_N, PRIMARY_ESCALATION. */
   @Builder.Default
   private String routingSelectionMode = "SINGLE";

   /** Number of candidates requested when routingSelectionMode is TOP_N. */
   @Builder.Default
   private int routingTopN = 1;

   /** Optional profile used for the escalation candidate. */
   private String routingEscalationProfile;

   /** Optional hard maximum estimated provider cost for this request. */
   private BigDecimal maximumRequestCost;

   /** Optional remaining aggregate workflow budget supplied by orchestration. */
   private BigDecimal remainingWorkflowBudget;

   /** Optional first-class RAG request configuration. */
   @Builder.Default
   @Valid
   private RagRequest rag = RagRequest.builder().build();

}