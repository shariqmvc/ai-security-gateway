package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.RoutingDecisionMetadata;
import com.ai.gateway.routing.RoutingStrategy;
import com.ai.gateway.multimodal.MediaContent;
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

    @Builder.Default
    private List<MediaContent> media = Collections.emptyList();

    /** 6.7 feedback context retained with the actual provider invocation. */
    private RoutingDecisionMetadata routingDecisionMetadata;
    private RoutingStrategy routingStrategy;
}
