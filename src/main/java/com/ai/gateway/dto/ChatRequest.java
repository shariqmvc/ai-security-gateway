package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import lombok.*;

import java.util.Collections;
import java.util.Set;
import java.math.BigDecimal;
import com.ai.gateway.rag.api.RagRequest;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

   // @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
    private Provider provider;
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
   private RagRequest rag = RagRequest.builder().build();

}