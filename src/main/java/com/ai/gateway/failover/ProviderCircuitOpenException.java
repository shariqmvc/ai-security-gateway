package com.ai.gateway.failover;

import com.ai.gateway.enums.Provider;

/**
 * Raised when a provider/model is temporarily excluded by the local circuit breaker.
 */
public class ProviderCircuitOpenException extends RuntimeException {

    private final Provider provider;
    private final String model;
    private final long retryAfterMs;

    public ProviderCircuitOpenException(
            Provider provider,
            String model,
            long retryAfterMs) {

        super(
                "Provider circuit is open for "
                        + provider
                        + "/"
                        + model
                        + "; retry after "
                        + retryAfterMs
                        + " ms.");

        this.provider = provider;
        this.model = model;
        this.retryAfterMs = retryAfterMs;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
