package com.ai.gateway.core.contract;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.RoutingDecisionMetadata;
import com.ai.gateway.core.routing.RoutingStrategy;
import com.ai.gateway.core.multimodal.MediaContent;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIRequest {

    private Provider provider;
    private String model;
    private String prompt;

    /** Opaque execution/billing mode resolved by the application layer. */
    private String billingMode;

    @Builder.Default
    private List<MediaContent> media = Collections.emptyList();

    /** 6.7 feedback context retained with the actual provider invocation. */
    private RoutingDecisionMetadata routingDecisionMetadata;
    private RoutingStrategy routingStrategy;
}
