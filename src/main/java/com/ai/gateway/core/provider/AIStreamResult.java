package com.ai.gateway.core.provider;

import lombok.Builder;
import lombok.Value;
import java.util.List;
import com.ai.gateway.core.model.Provider;

@Value
@Builder
public class AIStreamResult {
    String response;
    com.ai.gateway.core.model.Provider provider;
    String model;
    Integer inputTokens;
    Integer outputTokens;
    Integer totalTokens;
    Long latencyMs;
    String finishReason;

    /** Time from provider request start until the first streamed token/delta. */
    Long timeToFirstTokenMs;

    /** Actual executable endpoint used for this stream. */
    String endpointId;

    /** Provider requested by the caller before streaming failover. */

    /** Endpoint requested before streaming failover. */
    String failoverFromEndpointId;

    /** Classified reason for the primary provider failure. */
    String failoverReason;

    /** Successful provider attempt number, where 1 is primary. */
    Integer providerAttempt;

    /** Ordered execution attempts. */
    @Builder.Default
    java.util.List<ProviderAttempt> providerAttempts = java.util.Collections.emptyList();

    public record ProviderAttempt(
            int attempt,
            com.ai.gateway.core.model.Provider provider,
            String model,
            String endpointId,
            String status,
            String failureType) {
    }

    com.ai.gateway.core.model.Provider failoverFromProvider;

}
