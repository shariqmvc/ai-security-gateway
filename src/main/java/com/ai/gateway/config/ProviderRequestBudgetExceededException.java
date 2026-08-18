package com.ai.gateway.config;

import org.springframework.web.client.ResourceAccessException;

/**
 * Indicates that the gateway request deadline expired before a provider call
 * could complete. This is a gateway budget decision, not a provider health
 * signal, so failover must not open the provider circuit for this exception.
 */
public class ProviderRequestBudgetExceededException
        extends ResourceAccessException {

    public ProviderRequestBudgetExceededException(String message) {
        super(message);
    }
}
