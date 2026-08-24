package com.ai.gateway.failover;

import com.ai.gateway.enums.Provider;

/**
 * Raised when an upstream AI provider rejects the gateway credentials.
 * This is a gateway/provider integration failure, not a client authentication failure.
 */
public class ProviderAuthenticationException extends RuntimeException {

    private final Provider provider;

    public ProviderAuthenticationException(Provider provider, Throwable cause) {
        super("Upstream provider authentication failed for " + provider + ".", cause);
        this.provider = provider;
    }

    public Provider getProvider() {
        return provider;
    }
}
