package com.ai.gateway.core.contract;

import com.ai.gateway.core.model.Provider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIResponse {

    private String response;

    private String providerRequestId;

    private Usage usage;

    /** Actual provider/model used after routing/failover. */
    private Provider provider;

    private String model;

}