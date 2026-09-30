package com.ai.gateway.core.contract;

import com.ai.gateway.core.model.Provider;
import lombok.*;

import java.util.Collections;
import java.util.Set;
import java.util.List;
import java.math.BigDecimal;
import com.ai.gateway.rag.api.RagRequest;
import com.ai.gateway.core.multimodal.MediaContent;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {
   @Builder.Default @Valid
   private java.util.List<ContextMessage> contextMessages = java.util.Collections.emptyList();
   @NotBlank(message = "Prompt cannot be empty") private String prompt;
   private Provider provider;
   @Builder.Default @Valid @Size(max = 8, message = "A maximum of 8 media items is allowed per request.")
   private java.util.List<MediaContent> media = java.util.Collections.emptyList();
   private String model;
   private String billingMode;
   @Builder.Default private String securityMode = "PROTECTED";
   @Builder.Default private String fileProcessingMode = "AUTO";
   @Builder.Default private Set<String> requiredCapabilities = Collections.emptySet();
   private boolean extensiveResearch;
   private String executionRole;
   private String routingPriority;
   private String routingOptimizationProfile;
   @Builder.Default private String routingSelectionMode = "SINGLE";
   @Builder.Default private int routingTopN = 1;
   private String routingEscalationProfile;
   private BigDecimal maximumRequestCost;
   private BigDecimal remainingWorkflowBudget;

   /** Ordered provider preference for policy routing. */
   @Builder.Default private List<Provider> preferredProviders = List.of();
   /** Providers explicitly excluded from policy routing. */
   @Builder.Default private Set<Provider> excludedProviders = Set.of();
   /** Whether provider failover may be used for this request. */
   @Builder.Default private boolean allowProviderFallbacks = true;

   @Builder.Default @Valid
   private RagRequest rag = RagRequest.builder().build();
}
