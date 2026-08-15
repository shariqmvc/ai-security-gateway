package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import lombok.*;

import java.util.Collections;
import java.util.Set;

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

}