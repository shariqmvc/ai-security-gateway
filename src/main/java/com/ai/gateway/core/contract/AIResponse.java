package com.ai.gateway.core.contract;

import com.ai.gateway.core.model.Provider;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class AIResponse {

    private String response;

    private String providerRequestId;

    private Usage usage;

    /** Actual provider/model used after routing/failover. */
    private Provider provider;

    private String model;

    /** Actual endpoint that generated this response. */
    private String endpointId;

    /** Provider-native generation termination reason, when supplied. */
    private String finishReason;

    /** Primary execution target before failover, when failover occurred. */
    private String failoverFromEndpointId;

    /** Number of the successful provider attempt, where 1 is the primary. */
    private Integer providerAttempt;

    /** Ordered execution attempts for this inference. */
    @Builder.Default
    private List<ProviderAttempt> providerAttempts = Collections.emptyList();

    public record ProviderAttempt(
            int attempt,
            Provider provider,
            String model,
            String endpointId,
            String status,
            String failureType) {
    }
}