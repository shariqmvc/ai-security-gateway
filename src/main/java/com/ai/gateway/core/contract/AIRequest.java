package com.ai.gateway.core.contract;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.RoutingDecisionMetadata;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.multimodal.MediaContent;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIRequest {
    private Provider provider;
    private String model;
    private String prompt;
    private String billingMode;
    private java.math.BigDecimal maximumRequestCost;
    private UUID personalAccountId;

    @Builder.Default
    private List<MediaContent> media = Collections.emptyList();

    private String fileProcessingMode;
    private RoutingDecisionMetadata routingDecisionMetadata;
    private RoutingStrategy routingStrategy;
    private String endpointId;

    @Builder.Default
    private boolean allowProviderFallbacks = true;

    /** Ordered candidates selected by the routing engine, including primary. */
    @Builder.Default
    private List<RoutingCandidate> routingCandidates = Collections.emptyList();
}
